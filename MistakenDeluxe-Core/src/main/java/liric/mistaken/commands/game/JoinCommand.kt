package liric.mistaken.commands.game

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.proxy.BungeeUtils
import org.bukkit.entity.Player

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
                    // 🔥 MODO VELOCITY: Lo mandamos al servidor de arenas
                    val arenaServer = plugin.config.getString("proxy-arena-server", "arenas") ?: "arenas"
                    player.sendMessage(pumpking.lib.color.ColorTranslator.translate("<green>Conectando al servidor de juegos..."))
                    BungeeUtils.sendToServer(plugin, player, arenaServer)
                } else {
                    // 🔥 MODO MULTIARENA LOCAL: Lo metemos a una sesión de cristal (Pre-Lobby)
                    val currentSession = plugin.sessionManager.getSession(player)
                    if (currentSession != null) {
                        player.sendMessage(pumpking.lib.color.ColorTranslator.translate("<red>Ya estás dentro de una partida."))
                        return@executes 0
                    }

                    val maxPlayers = plugin.config.getInt("settings.max-players-per-arena", 10)

                    // Buscar una partida que esté esperando jugadores
                    var targetSession = plugin.sessionManager.activeSessions.values.firstOrNull {
                        (it.currentState == GameState.LOBBY || it.currentState == GameState.VOTING) && it.getPlayers().size < maxPlayers
                    }

                    // Si todo está lleno o en juego, creamos una nueva arena
                    if (targetSession == null) {
                        targetSession = plugin.sessionManager.createSession("Votando...")
                    }

                    // Meter al jugador y aislarlo
                    plugin.sessionManager.joinSession(player, targetSession.id)

                    plugin.lobbyLocation?.let { preLobby ->
                        player.teleportAsync(preLobby).thenAccept {
                            plugin.isolationManager.updateVisibility(player)
                        }
                    }

                    player.sendMessage(pumpking.lib.color.ColorTranslator.translate("<green>¡Te has unido a la partida! <gray>[${targetSession.id}]"))

                    // Revisar si ya son suficientes para arrancar el contador
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
