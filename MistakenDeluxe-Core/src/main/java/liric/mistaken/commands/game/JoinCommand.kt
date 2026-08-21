package liric.mistaken.commands.game

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.misc.BungeeUtils
import org.bukkit.entity.Player
import liric.mistaken.utils.color.ColorTranslator

/**
 * [LIRIC-MISTAKEN 2.0]
 * JoinCommand: El Matchmaking (Emparejamiento).
 * Conecta el Lobby con el Servidor de Juegos o te mete a una arena local.
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

                val serverMode = plugin.serverMode

                if (serverMode == "NETWORK_LOBBY") {
                    
                    val arenaServer = plugin.config.getString("proxy-arena-server", "arenas") ?: "arenas"
                    player.sendMessage(ColorTranslator.translate("<green>Conectando al servidor de juegos..."))
                    BungeeUtils.sendToServer(plugin, player, arenaServer)
                } else {
                    
                    val currentSession = plugin.sessionManager.getSession(player)
                    if (currentSession != null) {
                        player.sendMessage(ColorTranslator.translate("<red>Ya estás dentro de una partida."))
                        return@executes 0
                    }

                    val maxPlayers = plugin.config.getInt("settings.max-players-per-arena", 10)

                    
                    var targetSession = plugin.sessionManager.activeSessions.values.firstOrNull {
                        (it.currentState == GameState.LOBBY || it.currentState == GameState.VOTING || it.currentState == GameState.BREAK) && it.getPlayers().size < maxPlayers
                    }

                    
                    if (targetSession == null) {
                        targetSession = plugin.sessionManager.createSession("Votando...")
                    }

                    
                    plugin.sessionManager.joinSession(player, targetSession.id)

                    plugin.lobbyLocation?.let { preLobby ->
                        player.teleportAsync(preLobby).thenAccept {
                            plugin.isolationManager.updateVisibility(player)
                        }
                    }

                    player.sendMessage(ColorTranslator.translate("<green>¡Te has unido a la partida! <gray>[${targetSession.id}]"))

                    
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
