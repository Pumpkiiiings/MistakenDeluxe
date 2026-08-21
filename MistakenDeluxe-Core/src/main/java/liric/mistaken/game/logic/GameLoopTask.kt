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

            val onlinePlayers = game.getPlayers() 
            tickCounter++
            val isSecondTick = tickCounter % 20 == 0

            if (isSecondTick || game.currentState == GameState.INGAME) {

                
                if (isSecondTick) {
                    if (game.timer > 0) game.timer--

                    val validCount = onlinePlayers.count { !game.plugin.isIgnored(it) }

                    val minPlayers = game.settings?.minPlayers ?: game.plugin.config.getInt("settings.min-players", 4)

                    
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

                            val arena = game.plugin.arenaManager.getArena(game.currentMapName)
                            if (arena != null && arena.timeMode == "dynamic") {
                                val maxDuration = game.settings?.gameDuration ?: game.plugin.config.getInt("settings.game-duration", 300)
                                val elapsed = maxDuration - game.timer
                                if (maxDuration > 0) {
                                    val targetTime = (18000.0 * elapsed / maxDuration).toLong()
                                    val aspWorld = onlinePlayers.firstOrNull()?.world
                                    if (aspWorld != null && targetTime <= 18000) {
                                        aspWorld.time = targetTime
                                    }
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
