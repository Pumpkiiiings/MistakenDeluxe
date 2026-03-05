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
        val arenas = plugin.arenaManager.getArenas()
        val winner = voteManager.getWinningMap(arenas) ?: run { resetToLobby(null); return }
        val arena = plugin.arenaManager.getArena(winner) ?: run { resetToLobby(null); return }

        currentState = GameState.STARTING
        currentMapName = winner

        // Usamos corrutinas para que el server ni sienta la carga del mundo
        scope.launch {
            val aspWorld = plugin.mapManager.loadArenaWorld(winner).join()

            withContext(plugin.bukkitDispatcher) {
                if (aspWorld == null) {
                    resetToLobby(null)
                    return@withContext
                }

                // --- 🛡️ FILTRO DE MODOS MEJORADO (SEGURIDAD TOTAL) ---
                timer = 15
                if (!modeForced) {
                    val onlineCount = Bukkit.getOnlinePlayers().count { !plugin.isIgnored(it) }

                    // 🔥 LA REGLA DE ORO 🔥
                    if (onlineCount < 3) {
                        // Si hay 1 o 2 vatos, no le buscamos ruido al chicharrón: CLASSIC sí o sí.
                        currentMode = MistakenMode.CLASSIC
                    } else {
                        // Si hay 3 o más, tiramos los dados
                        val chance = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 101)
                        var selected = when {
                            chance <= 60 -> MistakenMode.CLASSIC
                            chance <= 75 -> MistakenMode.ONE_BOUNCE
                            chance <= 90 -> MistakenMode.DOUBLE_KILLER
                            else -> MistakenMode.FREEZE_TAG
                        }

                        // Refinamos por si salió algo que ocupa más gente
                        // Double Killer a fuerza ocupa 4 o más (2 killers y al menos 2 víctimas)
                        if (selected == MistakenMode.DOUBLE_KILLER && onlineCount < 4) {
                            selected = MistakenMode.CLASSIC
                        }

                        currentMode = selected
                    }
                }
                // ---------------------------------------------------

                // Sincronizamos el mundo con los spawns
                arena.asesinoSpawn?.world = aspWorld
                arena.survivorSpawns.forEach { it.world = aspWorld }

                // Registramos generadores en lote (batch)
                val genLocations = arena.generators.map { loc ->
                    loc.clone().apply { world = aspWorld }
                }
                plugin.generatorManager.prepareArenaGenerators(genLocations)

                setupPlayers(arena)
                broadcastLocalized("game.map-loaded", Placeholder.parsed("map", winner))
            }
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

        // 1. RECOLECCIÓN DE DATOS PARA ASYNC (Antes de limpiar listas)
        val mapName = currentMapName ?: "---"
        val modeName = currentMode.name
        val killer = getCurrentAsesino()
        val ganadorNombre = if (killerWon) (killer?.name ?: "El Asesino") else "Supervivientes"
        val razon = if (killerWon) "¡El asesino ganó!" else "¡Los supervivientes sobrevivieron!"

        // Filtrar sobrevivientes que escaparon (vivos y en survival)
        val onlinePlayers = Bukkit.getOnlinePlayers().toList()
        val escapados = onlinePlayers.filter {
            !esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL
        }.map { it.name }

        // 2. 🚀 TAREAS PESADAS ASÍNCRONAS (Stats y Discord)
        plugin.pluginScope.launch(Dispatchers.IO) {
            // Discord Hook
            plugin.discordManager.sendGameEnd(mapName, ganadorNombre, razon, escapados)

            // Procesar estadísticas de todos
            onlinePlayers.forEach { p ->
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

        // 3. LIMPIEZA DE LÓGICA GLOBAL
        broadcastLocalized(configPath)
        combatManager.giveWinRewards(killerWon)
        ambientManager.stopAll()
        combatManager.clearAll()
        limpiarMapa() // ASP Unload si es necesario

        modeForced = false
        val type = if (killerWon) "killer" else "survivor"
        val winSound = if (killerWon) Sound.ENTITY_WITHER_SPAWN else Sound.UI_TOAST_CHALLENGE_COMPLETE

        // 4. 🌀 BUCLE ÚNICO DE LIMPIEZA DE JUGADORES (Optimizado)
        for (p in onlinePlayers) {
            // A. Limpiar transporte y fuego
            p.passengers.forEach { p.removePassenger(it) }
            p.vehicle?.removePassenger(p)
            p.fireTicks = 0

            // B. Limpiar Inventario y Efectos
            p.inventory.clear()
            p.inventory.armorContents = arrayOfNulls(4)
            p.activePotionEffects.toList().forEach { p.removePotionEffect(it.type) }

            // C. Ejecutar cleanup de la clase (Habilidades, Tareas, etc.)
            if (esAsesino(p.uniqueId)) {
                plugin.asesinoManager.getAsesinoDelJugador(p)?.cleanup(p)
            } else {
                plugin.supervivienteManager.getClase(p)?.cleanup(p)
            }

            // D. 🔥 FIX ESPECTADOR (Usando el nuevo utilitario)
            // Esto evita que caigan al vacío y asegura que puedan volar
            liric.mistaken.utils.SpectatorUtils.setSafeSpectator(p)

            // E. Feedback Visual y Auditivo
            p.showTitle(Title.title(
                plugin.messageConfig.getMessage(p, "game.$type-title"),
                plugin.messageConfig.getMessage(p, "game.$type-subtitle")
            ))
            p.playSound(p.location, winSound, 1f, 1f)
        }

        // 5. RESET FINAL DE ROLES
        asesinosUUIDs.clear()
        plugin.asesinoManager.removerTodosLosAsesinos()
        plugin.supervivienteManager.limpiarTodo()

        plugin.componentLogger.info(plugin.mm.deserialize("<gray>[Game] Partida finalizada correctamente.</gray>"))
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
        // Aseguramos que si el estado es LOBBY, busque "lobby" en el YAML
        val stateName = currentState.name.lowercase()

        // 1. Formateo de tiempo
        val mins = timer / 60
        val secs = timer % 60
        val timeStr = if (currentState == GameState.INGAME || currentState == GameState.STARTING) {
            String.format("%02d:%02d", mins, secs)
        } else {
            timer.toString()
        }

        val mapDisplay = if (currentState == GameState.LOBBY || currentState == GameState.VOTING) "Lobby" else currentMapName

        // 2. Firma optimizada
        val signature = "S:$stateName|T:$timeStr|O:$online|M:$mapDisplay|MD:${currentMode.name}"
        if (lastProcessedText[uuid] == signature) return
        lastProcessedText[uuid] = signature

        // 3. Resolvemos los tags (AÑADIDO ONLINE)
        val timeTag = Placeholder.parsed("time", timeStr)
        val mapTag = Placeholder.parsed("map", mapDisplay)
        val modeTag = Placeholder.parsed("mode", currentMode.name)
        val onlineTag = Placeholder.parsed("online", online.toString()) // 🔥 IMPORTANTE

        // 4. Obtener el componente
        // Cambia "messages" por el nombre del archivo donde realmente esté el bloque bossbar:
        val barComponent = plugin.messageConfig.getMessageFromFile(
            p,
            "messages",
            "bossbar.$stateName",
            timeTag, mapTag, modeTag, onlineTag
        )

        // 5. Gestión de la barra
        val bar = personalBars.getOrPut(uuid) {
            val color = getBossBarColor(p, stateName)
            val newBar = net.kyori.adventure.bossbar.BossBar.bossBar(
                barComponent,
                1.0f,
                color,
                net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS
            )
            p.showBossBar(newBar)
            newBar
        }

        bar.name(barComponent)

        // 6. Actualizar color y progreso si es necesario
        if (lastStateForColor != currentState) {
            bar.color(getBossBarColor(p, stateName))
            // lastStateForColor = currentState // No olvides actualizar esta variable
        }
    }

    private fun getBossBarColor(p: Player, stateName: String): net.kyori.adventure.bossbar.BossBar.Color {
        // 🔥 CORREGIDO: getRawString(jugador, path, default, archivo)
        val colorStr = plugin.messageConfig.getRawString(p, "bossbar.colors.$stateName", "WHITE", "messages")
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
