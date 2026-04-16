package liric.mistaken.commands.game

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.misc.BungeeUtils
import org.bukkit.entity.Player

/**
 *[LIRIC-MISTAKEN 2.0]
 * JoinCommand: El Matchmaking (Emparejamiento) optimizado para Folia.
 */
object JoinCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("jugar")
            .executes { context ->
                val player = context.source.sender as? Player
                if (player == null) {
                    context.source.sender.sendMessage("Solo jugadores pueden usar esto.")
                    return@executes 0
                }

                if (plugin.serverMode == "NETWORK_LOBBY") {
                    val arenaServer = plugin.config.getString("proxy-arena-server", "arenas") ?: "arenas"
                    player.sendMessage(plugin.mm.deserialize("<green>Conectando al servidor de juegos..."))
                    BungeeUtils.sendToServer(plugin, player, arenaServer)
                } else {
                    val sessionManager = plugin.sessionManager
                    if (sessionManager == null) {
                        player.sendMessage(plugin.mm.deserialize("<red>El sistema de partidas está desactivado aquí."))
                        return@executes 0
                    }

                    val currentSession = sessionManager.getSession(player)
                    if (currentSession != null) {
                        player.sendMessage(plugin.mm.deserialize("<red>Ya estás dentro de una partida."))
                        return@executes 0
                    }

                    val maxPlayers = plugin.config.getInt("settings.max-players-per-arena", 10)

                    var targetSession = sessionManager.activeSessions.values.firstOrNull {
                        (it.currentState == GameState.LOBBY || it.currentState == GameState.VOTING) && it.getPlayers().size < maxPlayers
                    }

                    if (targetSession == null) {
                        targetSession = sessionManager.createSession("Votando...")
                    }

                    sessionManager.joinSession(player, targetSession.sessionId)

                    plugin.lobbyLocation?.let { preLobby ->
                        // Folia: teleport asíncrono
                        player.teleportAsync(preLobby).thenAccept { success ->
                            if (success && player.isOnline) {
                                plugin.isolationManager?.updateVisibility(player)
                            }
                        }
                    }

                    player.sendMessage(plugin.mm.deserialize("<green>¡Te has unido a la partida! <gray>[${targetSession.sessionId}]"))

                    val minPlayers = plugin.config.getInt("settings.min-players", 2)
                    if (targetSession.getPlayers().size >= minPlayers && targetSession.currentState == GameState.LOBBY) {
                        targetSession.stateController.startBreakProcess()
                    }
                }

                Command.SINGLE_SUCCESS
            }
            .build()
    }
}
