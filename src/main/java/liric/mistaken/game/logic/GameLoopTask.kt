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

                    // Actualizar BossBars
                    onlinePlayers.forEach { p -> game.uiController.updatePersonalBar(p, onlinePlayers.size) }

                    when (game.currentState) {
                        GameState.LOBBY -> {
                            // 🔥 FIX: Aumentado el default a 4 jugadores
                            if (validCount >= game.plugin.config.getInt("settings.min-players", 4)) {
                                game.stateController.startBreakProcess()
                            }
                        }
                        GameState.BREAK -> {
                            // Si alguien se sale y ya no hay 4 personas, abortar misión
                            if (validCount < game.plugin.config.getInt("settings.min-players", 4)) {
                                game.stateController.resetToLobby("voting.not-enough-players")
                            } else if (game.timer <= 0) {
                                game.stateController.startVotingProcess()
                            }
                        }
                        GameState.VOTING -> {
                            if (validCount < game.plugin.config.getInt("settings.min-players", 4)) {
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

                            if (game.currentMode == MistakenMode.ASSASSIN_PVP) {
                                val alivePlayers = onlinePlayers.filter {
                                    !game.plugin.isIgnored(it) && it.gameMode == org.bukkit.GameMode.SURVIVAL
                                }

                                if (alivePlayers.size <= 1) {
                                    game.stateController.endGame("discord.reason_killer_won", true)
                                } else if (game.timer <= 0) {
                                    game.stateController.endGame("discord.reason_survivors_won", false)
                                }
                            }
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
