package liric.mistaken.commands.admin

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import org.bukkit.GameMode
import org.bukkit.entity.Player

object GameCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("game")
            .requires { source -> source.sender.hasPermission("mistaken.admin") }
            .then(
                Commands.literal("tp")
                    .then(
                        Commands.argument("id", StringArgumentType.word())
                            .suggests { _, builder ->
                                val sessionManager = plugin.sessionManager
                                if (sessionManager != null) {
                                    val activas = sessionManager.activeSessions.keys
                                    val search = builder.remaining.uppercase()
                                    activas.forEach { id ->
                                        if (id.uppercase().startsWith(search)) builder.suggest(id)
                                    }
                                }
                                builder.buildFuture()
                            }
                            .executes { context ->
                                val player = context.source.sender as? Player ?: return@executes 0

                                val sessionManager = plugin.sessionManager
                                if (sessionManager == null) {
                                    player.sendMessage(plugin.mm.deserialize("<red>El sistema de partidas está desactivado en este servidor."))
                                    return@executes 0
                                }

                                val id = StringArgumentType.getString(context, "id").uppercase()
                                val session = sessionManager.activeSessions[id]

                                if (session == null) {
                                    player.sendMessage(plugin.mm.deserialize("<red>No existe ninguna partida con el ID: $id"))
                                    return@executes 0
                                }

                                // Unir al admin a la sesión
                                sessionManager.joinSession(player, id)

                                val arenaObj = plugin.arenaManager?.getArena(session.currentMapName)
                                val spawn = arenaObj?.asesinoSpawn ?: arenaObj?.survivorSpawns?.firstOrNull()

                                // Folia: Cambiar propiedades físicas en su Scheduler
                                player.scheduler.run(plugin, { _ ->
                                    player.gameMode = GameMode.SPECTATOR

                                    if (spawn != null) {
                                        player.teleportAsync(spawn).thenAccept { success ->
                                            if (success) {
                                                player.sendMessage(plugin.mm.deserialize("<green>Teletransportado a la partida <yellow>$id</yellow> (<white>${session.currentMapName}</white>)"))
                                            }
                                        }
                                    } else {
                                        player.sendMessage(plugin.mm.deserialize("<yellow>Te uniste a la sesión, pero el mapa aún no tiene spawns configurados."))
                                    }
                                }, null)

                                Command.SINGLE_SUCCESS
                            }
                    )
            )
            .build()
    }
}
