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

        // Usamos el Global Scheduler de Paper (Apto para Folia)
        gameTask = game.plugin.server.globalRegionScheduler.runAtFixedRate(game.plugin, { _ ->
            if (!game.plugin.isReady) return@runAtFixedRate

            val onlinePlayers = game.getPlayers()
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
                        GameState.INGAME -> {
                            // 🔥 REVISIÓN DE MODO APOCALIPSIS (Chequea si debe soltar a Geoffrey en el segundo exacto)
                            game.stateController.checkGeoffreySpawn()

                            // 🔥 LÓGICA AISLADA: Solo se ejecuta para el modo ASSASSIN_PVP
                            if (game.currentMode == MistakenMode.ASSASSIN_PVP) {

                                // Filtramos jugadores en supervivencia (vivos)
                                val alivePlayers = onlinePlayers.filter {
                                    !game.plugin.isIgnored(it) && it.gameMode == org.bukkit.GameMode.SURVIVAL
                                }

                                if (alivePlayers.size <= 1) {
                                    // Si queda 1 o ninguno, alguien ganó la masacre
                                    // true -> simula victoria del asesino para que reparta bien las recompensas en este modo extremo
                                    game.stateController.endGame("discord.reason_killer_won", true)
                                } else if (game.timer <= 0) {
                                    // Se acabó el tiempo y hay 2+ vivos (Empate)
                                    game.stateController.endGame("discord.reason_survivors_won", false)
                                }
                            }
                        }
                        GameState.ENDING -> {
                            if (game.timer <= 0) {
                                // 🔥 Todo lo de limpiar el mapa y los jugadores ya lo hace startBreakProcess() por dentro.
                                // Solo mandamos a llamar a la función para continuar el ciclo de juego.
                                game.stateController.startBreakProcess()
                            }
                        }
                    }
                }

                // --- CADA TICK (INGAME) ---
                if (game.currentState == GameState.INGAME) {
                    // La lógica del resto de modos y ticks rápidos sigue totalmente intacta aquí
                    game.playerController.handleInGameTick(onlinePlayers, tickCounter)
                }
            }
        }, 1L, 1L)
    }

    fun stop() {
        gameTask?.cancel()
    }
}
