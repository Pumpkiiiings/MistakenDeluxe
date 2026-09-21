package liric.mistaken.commands.economy

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object BalanceCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("balance")
            .executes { context ->
                val sender = context.source.sender
                val player = sender as? Player
                if (player == null) {
                    sender.sendMessage(MessageService.getComponent(null, "errors.player-only"))
                    return@executes 0
                }
                
                val stats = plugin.statsManager.getStats(player.uniqueId)
                val amount = stats.coins.get()
                player.sendMessage(MessageService.getComponent(player, "economy.balance", 
                    Placeholder.parsed("amount", amount.toString())))
                
                Command.SINGLE_SUCCESS
            }
            .then(
                Commands.argument("target", StringArgumentType.word())
                    .requires { it.sender.hasPermission("mistaken.admin") }
                    .suggests { _, builder ->
                        Bukkit.getOnlinePlayers().forEach { p ->
                            if (p.name.lowercase().startsWith(builder.remainingLowerCase)) {
                                builder.suggest(p.name)
                            }
                        }
                        builder.buildFuture()
                    }
                    .executes { context ->
                        val sender = context.source.sender
                        val playerSender = sender as? Player
                        val targetName = StringArgumentType.getString(context, "target")
                        
                        val target = Bukkit.getPlayer(targetName)
                        if (target == null) {
                            sender.sendMessage(MessageService.getComponent(playerSender, "errors.player-not-found"))
                            return@executes 0
                        }
                        
                        val stats = plugin.statsManager.getStats(target.uniqueId)
                        val amount = stats.coins.get()
                        sender.sendMessage(MessageService.getComponent(playerSender, "economy.balance-other", 
                            Placeholder.parsed("amount", amount.toString()), 
                            Placeholder.parsed("player", target.name)))
                        
                        Command.SINGLE_SUCCESS
                    }
            )
            .build()
    }
}
