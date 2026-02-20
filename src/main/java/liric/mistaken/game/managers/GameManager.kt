package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.game.Arena
import liric.mistaken.utils.mainThread
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.supervivientes.clases.Civil
import net.kyori.adventure.bossbar.BossBar
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.title.Title
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.types.PrefixNode
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min

/**
 * [LIRIC-MISTAKEN 2.0]
 * GameManager: Cerebro del juego optimizado.
 * Mantiene toda la lógica original pero ejecutada sobre Kotlin Coroutines.
 */
class GameManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lastBarContent = mutableMapOf<UUID, String>()
    private val lastBarSignature = mutableMapOf<UUID, String>()
    private var cachedBarColor: net.kyori.adventure.bossbar.BossBar.Color? = null
    private var lastStateForColor: GameState? = null
    private var tickCounter = 0
    private var gameTask: ScheduledTask? = null
    val voteManager = VoteManager()
    val ambientManager = AmbientManager(plugin)
    val combatManager = CombatManager(plugin)

    var currentState = GameState.LOBBY
        private set
    var currentMode = MistakenMode.CLASSIC
        private set
    var timer = 0
        private set
    var currentMapName = "Esperando..."
        private set

    private var modeForced = false
    private var currentAsesinoUUID: UUID? = null

    // Colecciones Thread-Safe
    val asesinosUUIDs = ConcurrentHashMap.newKeySet<UUID>()
    private val yaJugaronAsesino = Collections.synchronizedList(ArrayList<UUID>())
    private val personalBars = ConcurrentHashMap<UUID, BossBar>()
    private val lastProcessedText = ConcurrentHashMap<UUID, String>()
    private val changedBlocks = ConcurrentHashMap<Location, Material>()

    init {
        runGameLoop()
    }

    fun getPrefix(): String {
        return mm.serialize(plugin.messageConfig.getMessage(null, "prefix"))
    }

    /**
     * Bucle principal del juego (Game Loop).
     * Reemplaza al BukkitRunnable para una gestión de hilos más eficiente.
     */

    private fun runGameLoop() {
        // 1. Limpiamos cualquier tarea anterior para evitar que se dupliquen (Memory Leak Fix)
        gameTask?.cancel()

        // 2. Cacheamos la config una sola vez fuera del bucle
        val minPlayers = plugin.config.getInt("settings.min-players", 2)

        // 3. Reemplazamos el 'scope.launch' por el Scheduler Global de Paper.
        // Esto corre en el Hilo Principal, eliminando los saltos de hilo que viste en Spark.
        gameTask = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { _ ->

            // Verificación de seguridad: si el plugin no está listo, no hacemos nada
            if (!plugin.isReady) return@runAtFixedRate

            val onlinePlayers = Bukkit.getOnlinePlayers()
            tickCounter++

            val isSecondTick = tickCounter % 20 == 0

            // Tu lógica original optimizada:
            if (isSecondTick || currentState == GameState.INGAME) {

                // --- LÓGICA DE SEGUNDO (Cada 20 ticks) ---
                if (isSecondTick) {
                    if (timer > 0) timer--

                    // Filtrado rápido de jugadores válidos
                    val validCount = onlinePlayers.count { !plugin.isIgnored(it) }

                    // Actualización de barras con CACHÉ (Evita procesar MiniMessage si no hay cambios)
                    for (p in onlinePlayers) {
                        updatePersonalBar(p, onlinePlayers.size)
                    }

                    when (currentState) {
                        GameState.LOBBY -> {
                            if (validCount >= minPlayers) startVotingProcess()
                        }
                        GameState.VOTING -> {
                            if (validCount < minPlayers) {
                                resetToLobby("voting.not-enough-players")
                            } else if (timer <= 0) {
                                startInGame()
                            }
                        }
                        GameState.STARTING -> handleStartingSequence()
                        GameState.ENDING -> {
                            if (timer <= 0) {
                                teleportAllToLobby()
                                resetToLobby(null)
                            }
                        }
                        else -> {}
                    }
                }

                // --- LÓGICA DE TICK (Solo en partida) ---
                if (currentState == GameState.INGAME) {
                    handleInGameTick(onlinePlayers, tickCounter)
                }
            }
        }, 1L, 1L) // Pulso de 1 tick (exactamente 50ms)
    }

    private fun handleStartingSequence() {
        when (timer) {
            12 -> Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 0.5f) }
            10 -> {
                broadcastLocalized("game.mode-reveal-start")
                Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.1f) }
            }
            8 -> {
                broadcastLocalized("game.mode-selected", Placeholder.parsed("mode", currentMode.name))
                Bukkit.getOnlinePlayers().forEach { p ->
                    p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f)
                    p.showTitle(Title.title(
                        plugin.messageConfig.getMessage(p, "modes.${currentMode.name.lowercase()}.title"),
                        plugin.messageConfig.getMessage(p, "modes.${currentMode.name.lowercase()}.subtitle"),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
                    ))
                }
            }
            0 -> {
                currentState = GameState.INGAME
                timer = plugin.config.getInt("settings.game-duration", 300)
                broadcastLocalized("game.hunt-start")
            }
        }
    }

    private fun handleInGameTick(players: Collection<Player>, ticks: Int) {
        if (asesinosUUIDs.isEmpty()) {
            endGame("game.killer-disconnected", false)
            return
        }

        // Cache de asesinos online para no buscarlos en cada iteración
        val killersOnline = asesinosUUIDs.mapNotNull { Bukkit.getPlayer(it) }.filter { it.isOnline }

        for (p in players) {
            val uuid = p.uniqueId
            if (plugin.isIgnored(p) || esAsesino(uuid) || p.gameMode == GameMode.SPECTATOR) continue

            // 1. Ambience (Distribuido para no saturar ticks)
            if ((ticks + (uuid.hashCode() and 0xFFFF)) % 5 == 0) {
                var closestKiller: Player? = null
                var minDist = Double.MAX_VALUE
                val pLoc = p.location

                for (k in killersOnline) {
                    if (k.world != pLoc.world) continue
                    val dist = pLoc.distanceSquared(k.location)
                    if (dist < minDist) {
                        minDist = dist
                        closestKiller = k
                    }
                }
                closestKiller?.let { ambientManager.playSurvivorAmbience(p) } // Removido el killer param si tu ambientmanager nuevo no lo usa, o ajústalo
            }

            // 2. Heartbeat
            if (ticks % 10 == 0 && killersOnline.isNotEmpty()) {
                checkHeartbeat(killersOnline[0])
            }

            // 3. Efectos de herido (Salud 1)
            if (ticks % 2 == 0 && currentMode != MistakenMode.FREEZE_TAG && combatManager.getHealth(p) == 1 && p.vehicle == null) {
                if (!p.isSwimming) p.isSwimming = true
                if (ticks % 40 == 0) {
                    p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 45, 0, false, false, false))
                    p.playSound(p.location, Sound.ENTITY_PLAYER_BREATH, 0.8f, 0.8f)
                }
            }

            // 4. Consumo de estamina al cargar
            if (ticks % 5 == 0 && p.passengers.isNotEmpty() && p.isSprinting) {
                plugin.playerDataManager.consumeStamina(uuid, 0.4)
            }
        }

        // Win Check cada segundo
        if (ticks % 20 == 0) {
            if (timer <= 0) endGame("game.victory-survivors", false)
            else checkWinCondition()
        }
    }

    fun startInGame() {
        val winner = voteManager.getWinningMap(plugin.arenaManager.getArenas())
        if (winner == null) { resetToLobby(null); return }

        val arena = plugin.arenaManager.getArena(winner)
        if (arena == null) { resetToLobby(null); return }

        currentState = GameState.STARTING
        currentMapName = winner

        // 1. Carga asíncrona del mundo (MapManager)
        plugin.mapManager.loadArenaWorld(winner).thenAccept { aspWorld ->
            // Volvemos al Main Thread para tocar la API de Bukkit
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (aspWorld == null) {
                    resetToLobby(null)
                    return@Runnable
                }

                // --- LÓGICA DE MODO Y TIEMPO ---
                timer = 15
                if (!modeForced) {
                    val chance = ThreadLocalRandom.current().nextInt(1, 101)
                    currentMode = when {
                        chance <= 60 -> MistakenMode.CLASSIC
                        chance <= 75 -> MistakenMode.ONE_BOUNCE
                        chance <= 90 -> MistakenMode.DOUBLE_KILLER
                        else -> MistakenMode.FREEZE_TAG
                    }
                }

                // 2. ACTUALIZACIÓN DE MUNDO EN LOCALIZACIONES
                arena.asesinoSpawn?.world = aspWorld
                arena.survivorSpawns.forEach { it.world = aspWorld }

                // --- 🔥 AQUÍ VA LA OPTIMIZACIÓN (REEMPLAZO DEL BUCLE) 🔥 ---

                // Preparamos la lista de localizaciones clonadas con el mundo correcto
                val genLocations = arena.generators.map { loc ->
                    loc.clone().apply { world = aspWorld }
                }

                // Llamamos al método Batch que ya optimizamos.
                // Internamente hace clearGenerators(), carga la config en IO y spawnea hologramas.
                plugin.generatorManager.prepareArenaGenerators(genLocations)

                // --- FIN DE LA OPTIMIZACIÓN ---

                // 3. Setup de jugadores y feedback
                setupPlayers(arena)
                broadcastLocalized("game.map-loaded", Placeholder.parsed("map", winner))
            })
        }
    }

    private fun setupPlayers(arena: Arena) {
        // 1. ELIMINADO: arena.asesinoSpawn?.chunk?.load()
        // ¿Por qué? teleportAsync ya carga el chunk de forma asíncrona sin laguear.

        val onlinePlayers = Bukkit.getOnlinePlayers().filter { !plugin.isIgnored(it) }.toMutableList()
        if (onlinePlayers.isEmpty()) return

        // --- CÁLCULO DE ROLES (Lógica de memoria, es rápida) ---
        asesinosUUIDs.clear()
        val candidatos = onlinePlayers.filter { !yaJugaronAsesino.contains(it.uniqueId) }.toMutableList()
        if (candidatos.isEmpty()) {
            yaJugaronAsesino.clear()
            candidatos.addAll(onlinePlayers)
        }
        candidatos.shuffle()

        val killersToSelect = when (currentMode) {
            MistakenMode.DOUBLE_KILLER -> if (onlinePlayers.size >= 4) 2 else 1
            MistakenMode.ONE_BOUNCE -> (onlinePlayers.size - 1).coerceAtLeast(1)
            else -> 1
        }

        for (i in 0 until min(killersToSelect, candidatos.size)) {
            val uuid = candidatos[i].uniqueId
            asesinosUUIDs.add(uuid)
            yaJugaronAsesino.add(uuid)
        }
        currentAsesinoUUID = asesinosUUIDs.firstOrNull()

        // --- PREPARACIÓN Y TELEPORT ASÍNCRONO ---
        var survivorIndex = 0
        val survivorsSolo = mutableListOf<Player>()

        for (p in onlinePlayers) {
            val uuid = p.uniqueId
            val isKiller = esAsesino(uuid)

            // Reseteo básico instantáneo (Hilo principal)
            p.inventory.clear()
            combatManager.resetHealth(p)
            p.gameMode = GameMode.SURVIVAL

            if (isKiller) {
                setLuckPermsPrefix(p, "<red>")
                val spawnLoc = arena.asesinoSpawn ?: p.world.spawnLocation

                // 🔥 TELEPORT ASÍNCRONO: No detiene el Main Thread
                p.teleportAsync(spawnLoc).thenAccept { success ->
                    if (success && p.isOnline) {
                        val claseID = plugin.playerDataManager.getSelectedKiller(uuid)
                        plugin.asesinoManager.equiparAsesino(p, claseID)
                    }
                }
            } else {
                survivorsSolo.add(p)
                setLuckPermsPrefix(p, "<green>")
                val finalIndex = survivorIndex++

                // Buscamos el spawn que le toca
                val spawns = arena.survivorSpawns
                val spawnLoc = if (spawns.isEmpty()) arena.asesinoSpawn ?: p.world.spawnLocation
                else spawns[finalIndex % spawns.size]

                // 🔥 TELEPORT ASÍNCRONO ESCALONADO (1 tick de diferencia cada 2 jugadores)
                val delayTicks = (finalIndex / 2).toLong()

                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    if (!p.isOnline) return@Runnable

                    p.teleportAsync(spawnLoc).thenAccept { success ->
                        if (success && p.isOnline) {
                            val idElegido = plugin.playerDataManager.getSelectedSurvivor(uuid)
                            val clase = plugin.supervivienteManager.getClasePorId(idElegido) ?: Civil()
                            plugin.supervivienteManager.registrarSuperviviente(p, clase)
                        }
                    }
                }, delayTicks)
            }

            // Títulos (Adventure API es muy ligera)
            val rp = if (isKiller) "killer" else "survivor"
            p.showTitle(Title.title(
                plugin.messageConfig.getMessage(p, "roles.$rp.title"),
                plugin.messageConfig.getMessage(p, "roles.$rp.subtitle")
            ))
        }

        // --- DISCORD (Totalmente fuera del hilo principal) ---
        scope.launch(Dispatchers.IO) {
            val killer = getCurrentAsesino()
            if (killer != null) {
                plugin.discordManager.sendGameStart(currentMapName, currentMode.name, survivorsSolo, killer)
            }
        }
    }

    fun endGame(configPath: String, killerWon: Boolean) {
        if (currentState == GameState.ENDING) return
        currentState = GameState.ENDING
        timer = 15

        // Discord & Stats Async
        scope.launch(Dispatchers.IO) {
            val ganadorNombre = if (killerWon) (getCurrentAsesino()?.name ?: "El Asesino") else "Supervivientes"
            val razon = if (killerWon) "¡El asesino ganó!" else "¡Los supervivientes lograron sobrevivir!"
            val escapados = Bukkit.getOnlinePlayers().filter {
                !esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL
            }.map { it.name }

            plugin.discordManager.sendGameEnd(currentMapName, ganadorNombre, razon, escapados)

            Bukkit.getOnlinePlayers().forEach { p ->
                val uuid = p.uniqueId
                if (killerWon) {
                    if (esAsesino(uuid)) plugin.statsManager.incrementStat(uuid, "wins_assassin")
                    else plugin.statsManager.incrementStat(uuid, "losses_survivor")
                } else {
                    if (esAsesino(uuid)) plugin.statsManager.incrementStat(uuid, "losses_assassin")
                    else if (p.gameMode != GameMode.SPECTATOR) plugin.statsManager.incrementStat(uuid, "wins_survivor")
                }
            }
        }

        broadcastLocalized(configPath)
        combatManager.giveWinRewards(killerWon)
        ambientManager.stopAll()
        combatManager.clearAll()
        limpiarMapa()

        modeForced = false
        val type = if (killerWon) "killer" else "survivor"
        val winSound = if (killerWon) Sound.ENTITY_WITHER_SPAWN else Sound.UI_TOAST_CHALLENGE_COMPLETE

        Bukkit.getOnlinePlayers().forEach { p ->
            p.passengers.forEach { p.removePassenger(it) }
            p.vehicle?.removePassenger(p)
            p.inventory.clear()
            p.inventory.armorContents = arrayOfNulls(4)
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.fireTicks = 0
            p.isSwimming = false

            if (esAsesino(p.uniqueId)) {
                plugin.asesinoManager.getAsesinoDelJugador(p)?.cleanup(p)
            } else {
                plugin.supervivienteManager.getClase(p)?.cleanup(p)
            }

            p.gameMode = GameMode.SPECTATOR
            p.showTitle(Title.title(
                plugin.messageConfig.getMessage(p, "game.$type-title"),
                plugin.messageConfig.getMessage(p, "game.$type-subtitle")
            ))
            p.playSound(p.location, winSound, 1f, 1f)
        }

        asesinosUUIDs.clear()
        plugin.asesinoManager.removerTodosLosAsesinos()
        plugin.supervivienteManager.limpiarTodo()
    }

    fun checkWinCondition() {
        if (currentState != GameState.INGAME) return

        val allSurvivors = Bukkit.getOnlinePlayers().filter {
            !esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL
        }

        if (allSurvivors.isEmpty()) {
            endGame("game.victory-killer", true)
            return
        }

        val freeSurvivors = allSurvivors.count { !combatManager.isFrozen(it) }

        if (freeSurvivors == 0) {
            if (currentMode == MistakenMode.FREEZE_TAG) {
                if (allSurvivors.size > 1) endGame("game.victory-killer", true)
            } else {
                endGame("game.victory-killer", true)
            }
        }
    }

    fun addProgress(block: Block, amount: Int, player: Player?) {
        if (player != null && esAsesino(player.uniqueId)) return
        val loc = block.location
        if (plugin.generatorManager.isCompleted(loc)) return

        plugin.generatorManager.addProgress(loc, amount)

        if (plugin.generatorManager.isCompleted(loc)) {
            if (!changedBlocks.containsKey(loc)) changedBlocks[loc] = block.type
            block.type = Material.SEA_LANTERN
            block.world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1f)

            broadcastLocalized("game.generator-repaired",
                Placeholder.parsed("current", getRepairedCount().toString()),
                Placeholder.parsed("total", plugin.generatorManager.getTotalGenerators().toString())
            )
            checkWinCondition()
        }
    }

    fun handlePlayerDeath(player: Player) {
        if (esAsesino(player.uniqueId)) {
            asesinosUUIDs.remove(player.uniqueId)
            player.gameMode = GameMode.SPECTATOR
            if (asesinosUUIDs.isEmpty() && currentState == GameState.INGAME) {
                endGame("game.victory-survivors", false)
            }
            return
        }

        player.gameMode = GameMode.SPECTATOR
        player.isSwimming = false
        ambientManager.stopAmbience(player)

        scope.launch(Dispatchers.IO) {
            plugin.statsManager.incrementStat(player.uniqueId, "deaths")
        }

        player.vehicle?.let { if (it is Player) soltarPasajero(it) }

        broadcastLocalized("game.player-died", Placeholder.parsed("player", player.name))
        player.playSound(player.location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f)

        getCurrentAsesino()?.let { killer ->
            scope.launch(Dispatchers.IO) { plugin.statsManager.incrementStat(killer.uniqueId, "kills") }
            val extra = ThreadLocalRandom.current().nextInt(10, 21)
            addTime(extra)
            broadcastLocalized("game.time-extended", Placeholder.parsed("seconds", extra.toString()))

            killer.getAttribute(Attribute.MAX_HEALTH)?.let {
                killer.health = min(it.value, killer.health + 40.0)
            }
            killer.playSound(killer.location, Sound.ENTITY_WITCH_DRINK, 1f, 0.8f)
        }
        checkWinCondition()
    }

    private fun updatePersonalBar(p: Player, online: Int) {
        val uuid = p.uniqueId
        val stateName = currentState.name.lowercase()

        // 1. Formateo de tiempo ultra-rápido (Interpolación de Kotlin)
        val timeStr = if (currentState == GameState.INGAME || currentState == GameState.STARTING) {
            val mins = timer / 60
            val secs = timer % 60
            "${if (mins < 10) "0" else ""}$mins:${if (secs < 10) "0" else ""}$secs"
        } else {
            timer.toString()
        }

        // 2. CREAR FIRMA DE ESTADO (Para saber si algo de verdad cambió)
        // Incluimos online, timer, estado y mapa.
        val signature = "S:$stateName|T:$timeStr|O:$online|M:$currentMapName|MD:${currentMode.name}"

        if (lastBarSignature[uuid] == signature) return
        lastBarSignature[uuid] = signature

        // --- A PARTIR DE AQUÍ SOLO SE EJECUTA SI HUBO UN CAMBIO REAL ---

        // 3. Obtener textos (Optimizado)
        val modeKey = currentMode.name.lowercase()
        val modeName = plugin.messageConfig.getRawString(p, "modes.$modeKey.name", currentMode.name)
        val rawText = plugin.messageConfig.getRawString(p, "bossbar.$stateName", "<gray>Mistaken Game")

        // Reemplazo de placeholders eficiente
        val processedText = rawText
            .replace("{online}", online.toString())
            .replace("{time}", timeStr)
            .replace("{map}", currentMapName ?: "...")
            .replace("{mode}", modeName)

        // 4. Gestión de la Barra
        val bar = personalBars.getOrPut(uuid) {
            val color = getBossBarColor(p, stateName)
            val newBar = net.kyori.adventure.bossbar.BossBar.bossBar(
                mm.deserialize(processedText),
                1.0f,
                color,
                net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS
            )
            p.showBossBar(newBar)
            newBar
        }

        // 5. Actualizar contenido visual
        bar.name(mm.deserialize(processedText))

        // 6. Actualizar color solo si el estado cambió (Ahorra lecturas de config)
        if (lastStateForColor != currentState) {
            bar.color(getBossBarColor(p, stateName))
            if (uuid == Bukkit.getOnlinePlayers().firstOrNull()?.uniqueId) {
                lastStateForColor = currentState // Solo lo actualizamos una vez por ciclo global
            }
        }
    }

    /**
     * Obtiene el color de la BossBar de forma segura y eficiente.
     */
    private fun getBossBarColor(p: Player, stateName: String): net.kyori.adventure.bossbar.BossBar.Color {
        val colorStr = plugin.messageConfig.getRawString(p, "bossbar.colors.$stateName", "WHITE")
        return try {
            net.kyori.adventure.bossbar.BossBar.Color.valueOf(colorStr.uppercase())
        } catch (e: Exception) {
            net.kyori.adventure.bossbar.BossBar.Color.WHITE
        }
    }

    fun removePlayer(p: Player) {
        val bar = personalBars.remove(p.uniqueId)
        if (bar != null) p.hideBossBar(bar)
        lastBarSignature.remove(p.uniqueId)
    }

    private fun checkHeartbeat(killer: Player) {
        val killerLoc = killer.location
        val distSqCerca = 36.0
        val distSqLejos = 225.0

        for (p in Bukkit.getOnlinePlayers()) {
            if (p.gameMode == GameMode.SPECTATOR || esAsesino(p.uniqueId)) continue
            if (p.world != killerLoc.world) continue

            val d2 = p.location.distanceSquared(killerLoc)
            if (d2 <= distSqLejos) {
                val pitch = if (d2 < distSqCerca) 1.4f else 0.8f
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7f, pitch)
            }
        }
    }

    fun removePlayerData(uuid: UUID) {
        val p = Bukkit.getPlayer(uuid)

        personalBars.remove(uuid)?.let { bar ->
            p?.hideBossBar(bar)
        }

        lastProcessedText.remove(uuid)

        p?.let {
            it.isSwimming = false
            plugin.ambientManager.stopAmbience(it)
        }

        asesinosUUIDs.remove(uuid)
        plugin.combatManager.removePlayerData(uuid)

        if (uuid == currentAsesinoUUID) {
            currentAsesinoUUID = null
        }
    }

    fun soltarPasajero(r: Player) { combatManager.soltarPasajero(r) }

    private fun limpiarMapa() {
        plugin.generatorManager.resetGenerators()
        changedBlocks.forEach { (loc, material) -> loc.block.type = material }
        changedBlocks.clear()

        for (p in Bukkit.getOnlinePlayers()) {
            setLuckPermsPrefix(p, "")
            p.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            p.health = 20.0
            p.isSwimming = false
        }
        voteManager.resetVotes()
    }

    private fun resetToLobby(path: String?) {
        path?.let { broadcastLocalized(it) }
        limpiarMapa()
        currentState = GameState.LOBBY
        currentAsesinoUUID = null
        asesinosUUIDs.clear()
        ambientManager.stopAll()
        combatManager.clearAll()
    }

    private fun setLuckPermsPrefix(player: Player, colorTag: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val lp = LuckPermsProvider.get()
                lp.userManager.modifyUser(player.uniqueId) { user ->
                    user.data().clear { it is PrefixNode }
                    if (colorTag.isNotEmpty()) {
                        user.data().add(PrefixNode.builder(colorTag, 100).build())
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun teleportAllToLobby() {
        plugin.lobbyLocation?.let { loc ->
            Bukkit.getOnlinePlayers().forEach { p ->
                p.teleport(loc)
                p.gameMode = GameMode.SURVIVAL
            }
        }
    }

    fun broadcastLocalized(path: String, vararg tags: TagResolver) {
        Bukkit.getOnlinePlayers().forEach { p ->
            p.sendMessage(plugin.messageConfig.getMessage(p, path, *tags))
        }
    }

    // --- GETTERS MAESTROS ---
    fun getCurrentAsesino() = currentAsesinoUUID?.let { Bukkit.getPlayer(it) }
    fun esAsesino(uuid: UUID) = asesinosUUIDs.contains(uuid)
    fun setCurrentMode(mode: MistakenMode) { this.currentMode = mode; this.modeForced = true }
    fun getRepairedCount() = plugin.generatorManager.getCompletedCount()
    fun addTime(seconds: Int) { this.timer = min(this.timer + seconds, 900) }

    fun forceStart() {
        if (currentState == GameState.LOBBY || currentState == GameState.VOTING) {
            startVotingProcess()
            this.timer = 5
        }
    }

    private fun startVotingProcess() {
        if (currentState == GameState.VOTING) return
        currentState = GameState.VOTING
        timer = plugin.config.getInt("settings.voting-duration", 30)
        broadcastLocalized("voting.started")
        plugin.arenaManager.getArenas().keys.forEach { map ->
            broadcastLocalized("voting.map-option", Placeholder.parsed("map", map))
        }
    }
}
