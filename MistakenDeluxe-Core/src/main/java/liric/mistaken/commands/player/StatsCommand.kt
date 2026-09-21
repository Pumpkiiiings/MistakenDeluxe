package liric.mistaken.commands.player

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player

object StatsCommand {
    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("stats")
            .then(
                Commands.argument("target", StringArgumentType.word())
                    .requires { ctx -> ctx.sender.hasPermission("mistaken.admin") }
                    .suggests { _, builder: SuggestionsBuilder ->
                        Bukkit.getOnlinePlayers().forEach { p ->
                            if (p.name.lowercase().startsWith(builder.remainingLowerCase)) {
                                builder.suggest(p.name)
                            }
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        val player = sender as? Player ?: return@executes 0
                        
                        val targetName = StringArgumentType.getString(ctx, "target")
                        val target = Bukkit.getPlayer(targetName) ?: player
                        
                        enviarEstadisticas(plugin, player, target)
                        1
                    }
            )
            .executes { ctx ->
                val player = ctx.source.sender as? Player ?: return@executes 0
                
                if (plugin.serverMode == "GAME_SERVER") {
                    val gm = plugin.sessionManager.getSession(player)
                    if (gm?.currentState != liric.mistaken.game.enums.GameState.LOBBY && gm?.currentState != liric.mistaken.game.enums.GameState.VOTING && gm?.currentState != liric.mistaken.game.enums.GameState.BREAK) {
                        player.sendMessage(MessageService.getComponent(player, "errors.lobby-only-command"))
                        return@executes 0
                    }
                }
                
                enviarEstadisticas(plugin, player, player)
                1
            }
            .build()
    }

    private fun enviarEstadisticas(plugin: Mistaken, p: Player, target: Player) {
        val stats = plugin.statsManager.getStats(target.uniqueId)
        p.sendMessage(MessageService.getComponent(p, "stats.header", Placeholder.parsed("player", target.name)))
        p.sendMessage(MessageService.getComponent(p, "stats.wins-survivor", Placeholder.parsed("value", stats.winsSurvivor.get().toString())))
        p.sendMessage(MessageService.getComponent(p, "stats.wins-assassin", Placeholder.parsed("value", stats.winsAssassin.get().toString())))
        p.sendMessage(MessageService.getComponent(p, "stats.kills", Placeholder.parsed("value", stats.kills.get().toString())))
        p.sendMessage(MessageService.getComponent(p, "stats.deaths", Placeholder.parsed("value", stats.deaths.get().toString())))
        p.sendMessage(MessageService.getComponent(p, "stats.footer"))
        p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f)
    }
}
