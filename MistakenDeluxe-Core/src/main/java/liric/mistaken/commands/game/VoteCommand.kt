package liric.mistaken.commands.game

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
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
                sender.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "voting.usage"))
                1
            }
            .then(
                Commands.argument("mapName", StringArgumentType.greedyString())
                    .suggests { _, builder: SuggestionsBuilder ->
                        plugin.arenaManager.getArenas().keys.forEach { mapName ->
                            if (mapName.startsWith(builder.remainingLowerCase, ignoreCase = true)) {
                                builder.suggest(mapName)
                            }
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        val player = sender as? Player ?: return@executes 0

                        val session = plugin.sessionManager.getSession(player)
                        if (session == null) {
                            player.sendMessage(plugin.mm.deserialize("<red>No estás en ninguna partida activa para votar."))
                            return@executes 0
                        }

                        if (session.currentState != GameState.VOTING) {
                            player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "voting.not-active"))
                            return@executes 0
                        }

                        val voteManager = session.voteManager
                        if (voteManager.hasVoted(player.uniqueId)) {
                            player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "voting.already-voted"))
                            return@executes 0
                        }

                        val inputName = StringArgumentType.getString(ctx, "mapName")
                        val actualMapName = plugin.arenaManager.getArenas().keys
                            .firstOrNull { it.equals(inputName, ignoreCase = true) }

                        if (actualMapName == null) {
                            player.sendMessage(
                                pumpking.lib.service.PumpkingServiceManager.messages.getComponent(
                                    player,
                                    "voting.not-found",
                                    Placeholder.parsed("map", inputName)
                                )
                            )
                            return@executes 0
                        }

                        voteManager.addVote(player.uniqueId, actualMapName)

                        player.sendMessage(
                            pumpking.lib.service.PumpkingServiceManager.messages.getComponent(
                                player,
                                "voting.success",
                                Placeholder.parsed("map", actualMapName)
                            )
                        )
                        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)

                        Command.SINGLE_SUCCESS
                    }
            )
            .build()
    }
}
