package liric.mistaken.game.logic

import liric.mistaken.game.GameManager
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.Sound
import java.util.concurrent.ThreadLocalRandom

class GameStateController(private val game: GameManager) {

    // Cache local para el resultado de la partida
    private var lastKillerWon = false

    fun startBreakProcess() {
        // Detectamos si venimos de una partida que acaba de terminar (estado ENDING)
        val venimosDePartida = game.currentState == GameState.ENDING

        if (game.currentState == GameState.BREAK) return
        game.currentState = GameState.BREAK

        // Configuración del tiempo de descanso
        game.timer = game.plugin.config.getInt("settings.break-duration", 10)

        // --- LIMPIEZA POST-CINEMÁTICA ---
        if (venimosDePartida) {
            // Limpiamos todo: esto quita el Glow, resetea vida, quita el dummy de la cinemática, etc.
            game.playerController.cleanupAllPlayers(lastKillerWon)
            game.worldController.limpiarMapa()

            // Devolvemos a todos al punto de spawn del Lobby
            game.playerController.teleportAllToLobby()
        }

        game.broadcastLocalized("game.break-start")
    }

    fun startVotingProcess() {
        if (game.currentState == GameState.VOTING) return
        game.currentState = GameState.VOTING
        game.timer = game.plugin.config.getInt("settings.voting-duration", 30)

        game.broadcastLocalized("voting.started")
        game.plugin.arenaManager.getArenas().keys.forEach { map ->
            game.broadcastLocalized("voting.map-option", Placeholder.parsed("map", map))
        }
    }

    fun handleStartingSequence() {
        val online = game.plugin.server.onlinePlayers
        when (game.timer) {
            12 -> online.forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 0.5f) }
            10 -> {
                game.broadcastLocalized("game.mode-reveal-start")
                online.forEach { it.playSound(it.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.1f) }
            }
            8 -> {
                game.broadcastLocalized("game.mode-selected", Placeholder.parsed("mode", game.currentMode.name))
                game.uiController.playModeTitle(online)
            }
            0 -> {
                game.currentState = GameState.INGAME
                game.timer = game.plugin.config.getInt("settings.game-duration", 300)
                game.broadcastLocalized("game.hunt-start")
            }
        }
    }

    fun startInGame() {
        val arenas = game.plugin.arenaManager.getArenas()
        val winner = game.voteManager.getWinningMap(arenas) ?: run { resetToLobby(null); return }
        val arena = game.plugin.arenaManager.getArena(winner) ?: run { resetToLobby(null); return }

        game.currentState = GameState.STARTING
        game.currentMapName = winner

        game.plugin.mapManager.loadArenaWorld(winner).thenAccept { aspWorld ->
            game.plugin.server.globalRegionScheduler.run(game.plugin) { _ ->
                if (aspWorld == null) {
                    resetToLobby(null)
                    return@run
                }

                game.timer = 15
                determineGameMode()

                arena.asesinoSpawn?.world = aspWorld
                arena.survivorSpawns.forEach { it.world = aspWorld }

                val genLocations = arena.generators.map { it.clone().apply { world = aspWorld } }
                game.plugin.generatorManager.prepareArenaGenerators(genLocations)

                game.playerController.setupPlayers(arena)
                game.broadcastLocalized("game.map-loaded", Placeholder.parsed("map", winner))
            }
        }
    }

    private fun determineGameMode() {
        if (game.modeForced) return
        val onlineCount = game.plugin.server.onlinePlayers.count { !game.plugin.isIgnored(it) }

        if (onlineCount < 3) {
            game.currentMode = MistakenMode.CLASSIC
        } else {
            val chance = ThreadLocalRandom.current().nextInt(1, 101)
            var selected = when {
                chance <= 60 -> MistakenMode.CLASSIC
                chance <= 75 -> MistakenMode.ONE_BOUNCE
                chance <= 90 -> MistakenMode.DOUBLE_KILLER
                else -> MistakenMode.ASSASSIN_PVP
            }
            if (selected == MistakenMode.DOUBLE_KILLER && onlineCount < 4) selected = MistakenMode.CLASSIC
            game.currentMode = selected
        }
    }

    fun endGame(configPath: String, killerWon: Boolean) {
        if (game.currentState == GameState.ENDING) return
        game.currentState = GameState.ENDING

        // Sincronizamos el resultado con el GameManager y localmente
        this.lastKillerWon = killerWon
        game.lastKillerWon = killerWon

        // 12 segundos para dar tiempo a la cinemática de 10 segundos
        game.timer = 12

        val mapName = game.currentMapName
        val killer = game.getCurrentAsesino()

        val defaultAssassinWord = game.plugin.messageConfig.getRawString(null, "words.assassin", "El Asesino", "messages")
        val defaultSurvivorsWord = game.plugin.messageConfig.getRawString(null, "words.survivors", "Supervivientes", "messages")

        val ganadorNombre = if (killerWon) (killer?.name ?: defaultAssassinWord) else defaultSurvivorsWord
        val razon = if (killerWon) {
            game.plugin.messageConfig.getRawString(null, "discord.reason_killer_won", "¡El asesino ganó!", "messages")
        } else {
            game.plugin.messageConfig.getRawString(null, "discord.reason_survivors_won", "¡Los supervivientes escaparon!", "messages")
        }

        val escapados = game.plugin.server.onlinePlayers.filter {
            !game.esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL
        }.map { it.name }

        // Tareas en segundo plano
        game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
            game.plugin.discordManager.sendGameEnd(mapName, ganadorNombre, razon, escapados)
            game.plugin.server.onlinePlayers.forEach { p ->
                val uuid = p.uniqueId
                if (killerWon) {
                    if (game.esAsesino(uuid)) game.plugin.statsManager.incrementStat(uuid, "wins_assassin")
                    else game.plugin.statsManager.incrementStat(uuid, "losses_survivor")
                } else {
                    if (game.esAsesino(uuid)) game.plugin.statsManager.incrementStat(uuid, "losses_assassin")
                    else if (p.gameMode != GameMode.SPECTATOR) game.plugin.statsManager.incrementStat(uuid, "wins_survivor")
                }
            }
        }

        // --- LÓGICA DE CINEMÁTICA ---
        if (killerWon && killer != null) {
            val claseAsesino = game.plugin.asesinoManager.getAsesinoDelJugador(killer)
            if (claseAsesino != null) {
                // playKillerOutro ahora se encarga de teletransportar a todos (vivos y muertos)
                // al centro para que nadie se pierda la escena.
                game.plugin.cinematicManager.playKillerOutro(killer, claseAsesino)
            } else {
                game.broadcastLocalized(configPath)
            }
        } else {
            // Si ganan supervivientes, mensaje normal y sonidos de victoria
            game.broadcastLocalized(configPath)
            game.plugin.server.onlinePlayers.forEach { it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f) }
        }

        game.combatManager.giveWinRewards(killerWon)
        game.modeForced = false
    }

    fun resetToLobby(path: String?) {
        path?.let { game.broadcastLocalized(it) }

        // Si hay una partida en curso o empezando, limpiar forzosamente
        if (game.currentState == GameState.INGAME || game.currentState == GameState.STARTING || game.currentState == GameState.ENDING) {
            game.playerController.cleanupAllPlayers(lastKillerWon)
            game.worldController.limpiarMapa()
            game.playerController.teleportAllToLobby()
        }

        game.currentState = GameState.LOBBY
        game.timer = 0
        game.currentAsesinoUUID = null
        game.asesinosUUIDs.clear()
        game.ambientManager.stopAll()
        game.combatManager.clearAll()
    }
}
