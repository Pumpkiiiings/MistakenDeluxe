package liric.mistaken.game.logic

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.game.GameManager
import liric.mistaken.game.enums.GameState

class GameLoopTask(private val game: GameManager) {
    private var gameTask: ScheduledTask? = null
    private var tickCounter = 0

    fun start() {
        gameTask?.cancel()

        // Usamos el Global Scheduler de Paper (Apto para Folia)
        gameTask = game.plugin.server.globalRegionScheduler.runAtFixedRate(game.plugin, { _ ->
            if (!game.plugin.isReady) return@runAtFixedRate

            val onlinePlayers = game.plugin.server.onlinePlayers
            tickCounter++
            val isSecondTick = tickCounter % 20 == 0

            if (isSecondTick || game.currentState == GameState.INGAME) {

                // --- CADA SEGUNDO ---
                if (isSecondTick) {
                    if (game.timer > 0) game.timer--

                    val validCount = onlinePlayers.count { !game.plugin.isIgnored(it) }

                    // Actualizar BossBars
                    onlinePlayers.forEach { p -> game.uiController.updatePersonalBar(p, onlinePlayers.size) }

                    when (game.currentState) {
                        GameState.LOBBY -> {
                            if (validCount >= game.plugin.config.getInt("settings.min-players", 2)) {
                                // En lugar de ir directo a votar, empezamos el descanso
                                game.stateController.startBreakProcess()
                            }
                        }
                        GameState.BREAK -> {
                            // Si alguien se sale y ya no hay gente suficiente, volvemos al Lobby
                            if (validCount < game.plugin.config.getInt("settings.min-players", 2)) {
                                game.stateController.resetToLobby("voting.not-enough-players")
                            } else if (game.timer <= 0) {
                                // Al terminar el descanso, iniciamos votaciones
                                game.stateController.startVotingProcess()
                            }
                        }
                        GameState.VOTING -> {
                            if (validCount < game.plugin.config.getInt("settings.min-players", 2)) {
                                game.stateController.resetToLobby("voting.not-enough-players")
                            } else if (game.timer <= 0) {
                                game.stateController.startInGame()
                            }
                        }
                        GameState.STARTING -> {
                            game.stateController.handleStartingSequence()
                        }
                        GameState.ENDING -> {
                            if (game.timer <= 0) {
                                game.playerController.teleportAllToLobby()
                                // 🔥 En lugar de ir a LOBBY con reset, vamos a BREAK para continuar el ciclo
                                game.stateController.startBreakProcess()
                            }
                        }
                        else -> {}
                    }
                }

                // --- CADA TICK (INGAME) ---
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
