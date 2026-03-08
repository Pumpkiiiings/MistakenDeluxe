package liric.mistaken.game.logic

import liric.mistaken.game.GameManager
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import java.util.concurrent.ThreadLocalRandom

class GameStateController(private val game: GameManager) {

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

        // 🚀 Carga de mundo usando CompletableFuture de Java en lugar de Coroutines
        game.plugin.mapManager.loadArenaWorld(winner).thenAccept { aspWorld ->
            // Volvemos al Hilo Principal/Global
            game.plugin.server.globalRegionScheduler.run(game.plugin) { _ ->
                if (aspWorld == null) {
                    resetToLobby(null)
                    return@run
                }

                game.timer = 15
                determineGameMode()

                // Setup del mapa
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
                else -> MistakenMode.FREEZE_TAG
            }
            if (selected == MistakenMode.DOUBLE_KILLER && onlineCount < 4) selected = MistakenMode.CLASSIC
            game.currentMode = selected
        }
    }

    fun endGame(configPath: String, killerWon: Boolean) {
        if (game.currentState == GameState.ENDING) return
        game.currentState = GameState.ENDING
        game.timer = 15

        val mapName = game.currentMapName
        val killer = game.getCurrentAsesino()
        val ganadorNombre = if (killerWon) (killer?.name ?: "El Asesino") else "Supervivientes"
        val razon = if (killerWon) "¡El asesino ganó!" else "¡Los supervivientes sobrevivieron!"

        val escapados = game.plugin.server.onlinePlayers.filter {
            !game.esAsesino(it.uniqueId) && it.gameMode == org.bukkit.GameMode.SURVIVAL
        }.map { it.name }

        // 🚀 Tareas IO Asíncronas nativas de Paper
        game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
            game.plugin.discordManager.sendGameEnd(mapName, ganadorNombre, razon, escapados)
            game.plugin.server.onlinePlayers.forEach { p ->
                val uuid = p.uniqueId
                if (killerWon) {
                    if (game.esAsesino(uuid)) game.plugin.statsManager.incrementStat(uuid, "wins_assassin")
                    else game.plugin.statsManager.incrementStat(uuid, "losses_survivor")
                } else {
                    if (game.esAsesino(uuid)) game.plugin.statsManager.incrementStat(uuid, "losses_assassin")
                    else if (p.gameMode != org.bukkit.GameMode.SPECTATOR) game.plugin.statsManager.incrementStat(uuid, "wins_survivor")
                }
            }
        }

        game.broadcastLocalized(configPath)
        game.combatManager.giveWinRewards(killerWon)
        game.playerController.cleanupAllPlayers(killerWon)
        game.worldController.limpiarMapa()
        game.modeForced = false
    }

    fun resetToLobby(path: String?) {
        path?.let { game.broadcastLocalized(it) }
        game.worldController.limpiarMapa()
        game.currentState = GameState.LOBBY
        game.currentAsesinoUUID = null
        game.asesinosUUIDs.clear()
        game.ambientManager.stopAll()
        game.combatManager.clearAll()
    }
}
