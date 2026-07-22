package liric.mistaken.game.logic

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode

class GameLoopTask(private val game: GameSession) {
    private var gameTask: ScheduledTask? = null
    private var tickCounter = 0

    fun start() {
        gameTask?.cancel()

        gameTask = game.plugin.server.globalRegionScheduler.runAtFixedRate(game.plugin, { _ ->
            if (!game.plugin.isReady) return@runAtFixedRate

            val onlinePlayers = game.getPlayers() // Usar los de la sesión actual
            tickCounter++
            val isSecondTick = tickCounter % 20 == 0

            if (isSecondTick || game.currentState == GameState.INGAME) {

                // --- CADA SEGUNDO ---
                if (isSecondTick) {
                    if (game.timer > 0) game.timer--

                    val validCount = onlinePlayers.count { !game.plugin.isIgnored(it) }

                    val minPlayers = game.settings?.minPlayers ?: game.plugin.config.getInt("settings.min-players", 4)

                    // Actualizar BossBars
                    onlinePlayers.forEach { p -> game.uiController.updatePersonalBar(p, onlinePlayers.size) }

                    when (game.currentState) {
                        GameState.LOBBY -> {
                            if (game.isPrivate) {
                                if (game.forceStart) game.stateController.startBreakProcess()
                            } else {
                                if (validCount >= minPlayers || game.forceStart) {
                                    game.stateController.startBreakProcess()
                                }
                            }
                        }
                        GameState.BREAK -> {
                            // Si alguien se sale y ya no hay suficientes personas, abortar misión
                            if (validCount < minPlayers && !game.forceStart) {
                                game.stateController.resetToLobby("voting.not-enough-players")
                            } else if (game.timer <= 0) {
                                if (game.settings?.forcedMap != null) {
                                    game.stateController.startInGame()
                                } else {
                                    game.stateController.startVotingProcess()
                                }
                            }
                        }
                        GameState.VOTING -> {
                            if (validCount < minPlayers && !game.forceStart) {
                                game.stateController.resetToLobby("voting.not-enough-players")
                            } else if (game.timer <= 0) {
                                game.stateController.startInGame()
                            }
                        }
                        GameState.STARTING -> {
                            game.stateController.handleStartingSequence()
                        }
                        GameState.INGAME -> {
                            game.stateController.checkGeoffreySpawn()


                        }
                        GameState.ENDING -> {
                            if (game.timer <= 0) {
                                game.stateController.startBreakProcess()
                            }
                        }
                    }
                }

                // --- CADA TICK ---
                if (game.currentState == GameState.INGAME) {
                    game.playerController.handleInGameTick(onlinePlayers, tickCounter)
                }
            }
        }, 1L, 1L)
    }

    fun stop() {
        gameTask?.cancel()
    }
}
