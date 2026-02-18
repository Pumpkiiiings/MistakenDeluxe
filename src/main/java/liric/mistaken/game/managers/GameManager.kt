package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.game.Arena
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.supervivientes.Superviviente
import liric.mistaken.supervivientes.clases.Civil
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.title.Title
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.types.PrefixNode
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min

/**
 * [LIRIC-MISTAKEN 2.0]
 * GameManager Maestro: Control total del ciclo de vida del juego.
 * Diseñado para ser Zero-Lag incluso en servidores de bajos recursos.
 */
class GameManager(private val plugin: Mistaken) {

    private val mm = MiniMessage.miniMessage()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Sub-Managers
    val voteManager = VoteManager()
    val ambientManager = AmbientManager(plugin)
    val combatManager = CombatManager(plugin)

    // Estado del Juego
    var currentState = GameState.LOBBY
        private set
    var currentMode = MistakenMode.CLASSIC
        private set
    var timer = 0
        private set
    var currentMapName = "Esperando..."
        private set

    private var isModeForced = false
    private val yaJugaronAsesino = mutableListOf<UUID>()
    private val asesinosUUIDs = ConcurrentHashMap.newKeySet<UUID>()
    private var currentAsesinoUUID: UUID? = null
    private val changedBlocks = ConcurrentHashMap<Location, Material>()

    // Cache visual para optimización de paquetes
    private val personalBars = ConcurrentHashMap<UUID, BossBar>()
    private val lastProcessedText = ConcurrentHashMap<UUID, String>()

    init {
        runGameLoop()
    }

    /**
     * Motor de juego principal.
     * Ejecuta lógica pesada solo cuando es necesario (cada segundo).
     */
    private fun runGameLoop() {
        scope.launch {
            var tickCounter = 0
            while (isActive) {
                tickCounter++
                val onlinePlayers = Bukkit.getOnlinePlayers()
                val validPlayers = onlinePlayers.filter { !plugin.isIgnored(it) }

                // Lógica de Segundo (Cada 20 ticks)
                if (tickCounter % 20 == 0) {
                    if (timer > 0) timer--

                    // Actualizar BossBars de forma reactiva
                    onlinePlayers.forEach { updatePersonalBar(it, onlinePlayers.size) }

                    when (currentState) {
                        GameState.LOBBY -> {
                            val minPlayers = plugin.config.getInt("settings.min-players", 2)
                            if (validPlayers.size >= minPlayers) {
                                withContext(Dispatchers.Main) { startVotingProcess() }
                            }
                        }
                        GameState.VOTING -> {
                            val minPlayers = plugin.config.getInt("settings.min-players", 2)
                            if (validPlayers.size < minPlayers) {
                                withContext(Dispatchers.Main) { resetToLobby("voting.not-enough-players") }
                            } else if (timer <= 0) {
                                withContext(Dispatchers.Main) { startInGame() }
                            }
                        }
                        GameState.STARTING -> withContext(Dispatchers.Main) { handleStartingSequence() }
                        GameState.ENDING -> {
                            if (timer <= 0) withContext(Dispatchers.Main) {
                                teleportAllToLobby()
                                resetToLobby(null)
                            }
                        }
                        else -> {}
                    }
                }

                // Lógica Intensa In-Game
                if (currentState == GameState.INGAME) {
                    handleInGameTick(onlinePlayers, tickCounter)
                }

                delay(50L) // 1 tick de Minecraft
            }
        }
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

    private suspend fun handleInGameTick(players: Collection<Player>, ticks: Int) {
        if (asesinosUUIDs.isEmpty()) {
            withContext(Dispatchers.Main) { endGame("game.killer-disconnected", false) }
            return
        }

        // Obtener asesinos online (solo una vez por tick)
        val killersOnline = asesinosUUIDs.mapNotNull { Bukkit.getPlayer(it) }.filter { it.isOnline }

        players.forEach { p ->
            val uuid = p.uniqueId
            if (plugin.isIgnored(p) || esAsesino(uuid) || p.gameMode == GameMode.SPECTATOR) return@forEach

            // 1. Latidos y Ambiente (Optimizado por distancia)
            if ((ticks + (uuid.hashCode() and 0xFFFF)) % 5 == 0) {
                val closestKiller = killersOnline.minByOrNull { it.location.distanceSquared(p.location) }
                closestKiller?.let { ambientManager.playSurvivorAmbience(p) }
            }

            // 2. Corazón (Cada 10 ticks)
            if (ticks % 10 == 0 && killersOnline.isNotEmpty()) {
                checkHeartbeat(killersOnline[0])
            }

            // 3. Efectos de Salud Crítica (Salud 1)
            if (ticks % 2 == 0 && currentMode != MistakenMode.FREEZE_TAG && combatManager.getHealth(p) == 1 && p.vehicle == null) {
                withContext(Dispatchers.Main) {
                    if (!p.isSwimming) p.isSwimming = true
                    if (ticks % 40 == 0) {
                        p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 45, 0, false, false, false))
                        p.playSound(p.location, Sound.ENTITY_PLAYER_BREATH, 0.8f, 0.8f)
                    }
                }
            }

            // 4. Consumo de Estamina al cargar a alguien
            if (ticks % 5 == 0 && p.passengers.isNotEmpty() && p.isSprinting) {
                plugin.playerDataManager.consumeStamina(uuid, 0.4)
            }
        }

        // 5. Verificar victoria cada segundo
        if (ticks % 20 == 0) {
            withContext(Dispatchers.Main) {
                if (timer <= 0) endGame("game.victory-survivors", false)
                else checkWinCondition()
            }
        }
    }

    fun startInGame() {
        val winner = voteManager.getWinningMap(plugin.arenaManager.getArenas()) ?: run { resetToLobby(null); return }
        val arena = plugin.arenaManager.getArena(winner) ?: run { resetToLobby(null); return }

        currentState = GameState.STARTING
        currentMapName = winner

        // Carga de mundo asíncrona (SlimeWorld / ASP)
        scope.launch {
            val world = plugin.mapManager.loadArenaWorld(winner).join() ?: run {
                withContext(Dispatchers.Main) { resetToLobby(null) }
                return@launch
            }

            withContext(Dispatchers.Main) {
                timer = 15
                if (!isModeForced) {
                    val rnd = ThreadLocalRandom.current().nextInt(1, 101)
                    currentMode = when {
                        rnd <= 60 -> MistakenMode.CLASSIC
                        rnd <= 75 -> MistakenMode.ONE_BOUNCE
                        rnd <= 90 -> MistakenMode.DOUBLE_KILLER
                        else -> MistakenMode.FREEZE_TAG
                    }
                }

                // Configurar Arena
                arena.asesinoSpawn?.world = world
                arena.survivorSpawns.forEach { it.world = world }
                plugin.generatorManager.clearGenerators()
                arena.generators.forEach {
                    it.world = world
                    plugin.generatorManager.registerGenerator(it)
                }

                setupPlayers(arena)
                broadcastLocalized("game.map-loaded", Placeholder.parsed("map", winner))
            }
        }
    }

    private fun setupPlayers(arena: Arena) {
        val onlinePlayers = Bukkit.getOnlinePlayers().filter { !plugin.isIgnored(it) }.toMutableList()
        if (onlinePlayers.isEmpty()) return

        asesinosUUIDs.clear()

        // Selección de Asesino (Rotación)
        val candidatos = onlinePlayers.filter { it.uniqueId !in yaJugaronAsesino }.shuffled().toMutableList()
        if (candidatos.isEmpty()) {
            yaJugaronAsesino.clear()
            candidatos.addAll(onlinePlayers.shuffled())
        }

        val killersCount = when(currentMode) {
            MistakenMode.DOUBLE_KILLER -> if (onlinePlayers.size >= 4) 2 else 1
            MistakenMode.ONE_BOUNCE -> (onlinePlayers.size - 1).coerceAtLeast(1)
            else -> 1
        }

        repeat(killersCount.coerceAtMost(candidatos.size)) {
            val k = candidatos.removeAt(0)
            asesinosUUIDs.add(k.uniqueId)
            yaJugaronAsesino.add(k.uniqueId)
        }
        currentAsesinoUUID = asesinosUUIDs.firstOrNull()

        // Preparación de jugadores (Items, Roles, Teleport)
        onlinePlayers.forEachIndexed { index, p ->
            p.inventory.clear()
            combatManager.resetHealth(p)
            p.gameMode = GameMode.SURVIVAL

            val isKiller = esAsesino(p.uniqueId)

            if (isKiller) {
                val claseID = plugin.playerDataManager.getSelectedKiller(p.uniqueId)
                plugin.asesinoManager.equiparAsesino(p, claseID)
                arena.asesinoSpawn?.let { p.teleport(it) }
                setLuckPermsPrefix(p, "<red>")
            } else {
                setLuckPermsPrefix(p, "<green>")
                val selectedClase = plugin.playerDataManager.getSelectedSurvivor(p.uniqueId)

                // Teleport distribuido para evitar tirones de red
                scope.launch(Dispatchers.Main) {
                    delay((index / 5) * 50L) // 5 jugadores cada tick
                    if (p.isOnline) {
                        val spawn = arena.survivorSpawns.getOrNull(index % arena.survivorSpawns.size) ?: arena.asesinoSpawn
                        spawn?.let { p.teleport(it) }

                        val clase = plugin.supervivienteManager.getClasePorId(selectedClase) ?: Civil()
                        plugin.supervivienteManager.registrarSuperviviente(p, clase)
                    }
                }
            }

            val rolePath = if (isKiller) "killer" else "survivor"
            p.showTitle(Title.title(
                plugin.messageConfig.getMessage(p, "roles.$rolePath.title"),
                plugin.messageConfig.getMessage(p, "roles.$rolePath.subtitle")
            ))
        }

        // Discord Webhook (Async)
        scope.launch(Dispatchers.IO) {
            val killer = getCurrentAsesino()
            val survivors = onlinePlayers.filter { !esAsesino(it.uniqueId) }
            if (killer != null) {
                plugin.discordManager.sendGameStart(currentMapName, currentMode.name, survivors, killer)
            }
        }
    }

    fun endGame(configPath: String, killerWon: Boolean) {
        if (currentState == GameState.ENDING) return
        currentState = GameState.ENDING
        timer = 15

        // Stats y Discord (Async)
        scope.launch(Dispatchers.IO) {
            val survivorsNames = Bukkit.getOnlinePlayers()
                .filter { !esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL }
                .map { it.name }

            val winnerName = if (killerWon) (getCurrentAsesino()?.name ?: "Asesino") else "Supervivientes"
            val reason = if (killerWon) "¡Masacre total!" else "¡Escaparon!"

            plugin.discordManager.sendGameEnd(currentMapName, winnerName, reason, survivorsNames)

            // Procesar Stats de cada jugador
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

        val type = if (killerWon) "killer" else "survivor"
        val sound = if (killerWon) Sound.ENTITY_WITHER_SPAWN else Sound.UI_TOAST_CHALLENGE_COMPLETE

        Bukkit.getOnlinePlayers().forEach { p ->
            p.passengers.forEach { p.removePassenger(it) }
            p.vehicle?.removePassenger(p)
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = GameMode.SPECTATOR
            p.isSwimming = false

            if (esAsesino(p.uniqueId)) {
                plugin.asesinoManager.getAsesinoDelJugador(p)?.cleanup(p)
            } else {
                plugin.supervivienteManager.getClase(p)?.cleanup(p)
            }

            p.showTitle(Title.title(
                plugin.messageConfig.getMessage(p, "game.$type-title"),
                plugin.messageConfig.getMessage(p, "game.$type-subtitle")
            ))
            p.playSound(p.location, sound, 1f, 1f)
        }

        asesinosUUIDs.clear()
        plugin.asesinoManager.removerTodosLosAsesinos()
        plugin.supervivienteManager.limpiarTodo()
        isModeForced = false
    }

    fun checkWinCondition() {
        if (currentState != GameState.INGAME) return

        val survivors = Bukkit.getOnlinePlayers().filter {
            !esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL
        }

        if (survivors.isEmpty()) {
            endGame("game.victory-killer", true)
            return
        }

        val freeCount = survivors.count { !combatManager.isFrozen(it) }

        if (freeCount == 0) {
            // En Freeze Tag, si queda solo 1, no termina hasta que muera o escape
            if (currentMode == MistakenMode.FREEZE_TAG && survivors.size > 1) {
                endGame("game.victory-killer", true)
            } else if (currentMode != MistakenMode.FREEZE_TAG) {
                endGame("game.victory-killer", true)
            }
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

        scope.launch(Dispatchers.IO) { plugin.statsManager.incrementStat(player.uniqueId, "deaths") }

        broadcastLocalized("game.player-died", Placeholder.parsed("player", player.name))
        player.playSound(player.location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f)

        getCurrentAsesino()?.let { killer ->
            scope.launch(Dispatchers.IO) { plugin.statsManager.incrementStat(killer.uniqueId, "kills") }
            val extraTime = ThreadLocalRandom.current().nextInt(10, 21)
            addTime(extraTime)
            broadcastLocalized("game.time-extended", Placeholder.parsed("seconds", extraTime.toString()))

            killer.getAttribute(Attribute.MAX_HEALTH)?.let {
                killer.health = min(it.value, killer.health + 4.0)
            }
            killer.playSound(killer.location, Sound.ENTITY_WITCH_DRINK, 1f, 0.8f)
        }
        checkWinCondition()
    }

    fun addProgress(block: Block, amount: Int, player: Player?) {
        if (player != null && esAsesino(player.uniqueId)) return
        val loc = block.location
        if (plugin.generatorManager.isCompleted(loc)) return

        plugin.generatorManager.addProgress(loc, amount)

        if (plugin.generatorManager.isCompleted(loc)) {
            if (!changedBlocks.containsKey(loc)) changedBlocks[loc] = block.type
            block.setType(Material.SEA_LANTERN, false)

            broadcastLocalized("game.generator-repaired",
                Placeholder.parsed("current", plugin.generatorManager.getCompletedCount().toString()),
                Placeholder.parsed("total", plugin.generatorManager.getTotalGenerators().toString())
            )
            checkWinCondition()
        }
    }

    private fun updatePersonalBar(p: Player, online: Int) {
        val bar = personalBars.getOrPut(p.uniqueId) {
            val style = BarStyle.valueOf(plugin.config.getString("bossbar.style", "SOLID")!!.uppercase())
            Bukkit.createBossBar("", BarColor.WHITE, style)
        }

        if (!bar.players.contains(p)) bar.addPlayer(p)

        val stateName = currentState.name.lowercase()
        val timeStr = if (currentState == GameState.INGAME || currentState == GameState.STARTING)
            String.format("%02d:%02d", timer / 60, timer % 60) else timer.toString()

        val modeName = plugin.messageConfig.getRawString(p, "modes.${currentMode.name.lowercase()}.name", currentMode.name)
        val rawText = plugin.messageConfig.getRawString(p, "bossbar.$stateName", "...")

        val processedText = rawText
            .replace("{online}", online.toString())
            .replace("{time}", timeStr)
            .replace("{map}", currentMapName)
            .replace("{mode}", modeName)

        // Optimización: Solo enviar paquete de título si el texto cambió
        if (lastProcessedText[p.uniqueId] != processedText) {
            bar.setTitle(mm.serialize(processedText).toString()) // Nota: MiniMessage a String plano para BossBar
            lastProcessedText[p.uniqueId] = processedText

            val colorStr = plugin.messageConfig.getRawString(p, "bossbar.colors.$stateName", "WHITE")
            try { bar.color = BarColor.valueOf(colorStr.uppercase()) } catch (ignored: Exception) {}
        }
    }

    private fun checkHeartbeat(killer: Player) {
        val killerLoc = killer.location
        Bukkit.getOnlinePlayers().forEach { p ->
            if (p.gameMode == GameMode.SPECTATOR || esAsesino(p.uniqueId)) return@forEach
            if (p.world != killer.world) return@forEach

            val distSq = p.location.distanceSquared(killerLoc)
            if (distSq <= 225.0) { // 15 bloques
                val pitch = if (distSq < 36.0) 1.4f else 0.8f
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7f, pitch)
            }
        }
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

    private fun startVotingProcess() {
        if (currentState == GameState.VOTING) return
        currentState = GameState.VOTING
        timer = plugin.config.getInt("settings.voting-duration", 30)
        broadcastLocalized("voting.started")
        plugin.arenaManager.getArenas().keys.forEach { map ->
            broadcastLocalized("voting.map-option", Placeholder.parsed("map", map))
        }
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

    private fun limpiarMapa() {
        plugin.generatorManager.resetGenerators()
        changedBlocks.forEach { (loc, mat) -> loc.block.setType(mat, false) }
        changedBlocks.clear()
        Bukkit.getOnlinePlayers().forEach { p ->
            setLuckPermsPrefix(p, "")
            p.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            p.health = 20.0
            p.isSwimming = false
        }
        voteManager.resetVotes()
    }

    private fun teleportAllToLobby() {
        val lobby = plugin.lobbyLocation ?: return
        Bukkit.getOnlinePlayers().forEach { p ->
            p.teleport(lobby)
            p.gameMode = GameMode.SURVIVAL
        }
    }

    fun broadcastLocalized(path: String, vararg tags: TagResolver) {
        Bukkit.getOnlinePlayers().forEach { p ->
            p.sendMessage(plugin.messageConfig.getMessage(p, path, *tags))
        }
    }

    fun removePlayerData(uuid: UUID) {
        Bukkit.getPlayer(uuid)?.let { p ->
            personalBars.remove(uuid)?.removeAll()
            lastProcessedText.remove(uuid)
            p.isSwimming = false
            ambientManager.stopAmbience(p)
        }
        asesinosUUIDs.remove(uuid)
        combatManager.removePlayerData(uuid)
        if (uuid == currentAsesinoUUID) currentAsesinoUUID = null
    }

    // Getters
    fun esAsesino(uuid: UUID) = asesinosUUIDs.contains(uuid)
    fun getCurrentAsesino() = currentAsesinoUUID?.let { Bukkit.getPlayer(it) }
    fun addTime(seconds: Int) { timer = (timer + seconds).coerceAtMost(900) }
    fun forceStart() {
        if (currentState == GameState.LOBBY || currentState == GameState.VOTING) {
            startVotingProcess()
            timer = 5
        }
    }
}
