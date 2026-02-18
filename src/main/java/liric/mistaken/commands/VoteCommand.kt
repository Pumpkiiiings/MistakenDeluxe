package liric.mistaken.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.entity.Player

object VoteCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("vote")
            .executes { ctx ->
                val sender = ctx.source.sender
                val player = sender as? Player
                sender.sendMessage(plugin.messageConfig.getMessage(player, "voting.usage"))
                1
            }
            .then(
                Commands.argument("mapName", StringArgumentType.greedyString())
                    .suggests { _, builder ->
                        // 🔥 FIX: Aquí es donde estaba el detalle.
                        // Recorremos las llaves y sugerimos cada una por separado.
                        plugin.arenaManager.getArenas().keys.forEach { mapName ->
                            builder.suggest(mapName)
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        val player = sender as? Player

                        // A. Solo jugadores, bro
                        if (player == null) {
                            sender.sendMessage(plugin.messageConfig.getMessage(null, "errors.only-players"))
                            return@executes 0
                        }

                        // B. ¿Estamos en tiempo de votación?
                        if (plugin.gameManager.currentState != GameState.VOTING) {
                            player.sendMessage(plugin.messageConfig.getMessage(player, "voting.not-active"))
                            return@executes 0
                        }

                        val voteManager = plugin.gameManager.voteManager

                        // C. ¿Ya soltó su voto el compa?
                        if (voteManager.hasVoted(player.uniqueId)) {
                            player.sendMessage(plugin.messageConfig.getMessage(player, "voting.already-voted"))
                            return@executes 0
                        }

                        // D. Validación del Mapa
                        val inputName = StringArgumentType.getString(ctx, "mapName")

                        // Buscamos el mapa ignorando mayúsculas/minúsculas
                        val actualMapName = plugin.arenaManager.getArenas().keys
                            .firstOrNull { it.equals(inputName, ignoreCase = true) }

                        if (actualMapName == null) {
                            player.sendMessage(
                                plugin.messageConfig.getMessage(
                                    player,
                                    "voting.not-found",
                                    Placeholder.parsed("map", inputName)
                                )
                            )
                            return@executes 0
                        }

                        // E. ¡Voto registrado!
                        voteManager.addVote(player.uniqueId, actualMapName)

                        // F. Feedback visual y sonoro
                        player.sendMessage(
                            plugin.messageConfig.getMessage(
                                player,
                                "voting.success",
                                Placeholder.parsed("map", actualMapName)
                            )
                        )
                        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)

                        1
                    }
            )
            .build()
    }
}
