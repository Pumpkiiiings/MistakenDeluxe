package liric.mistaken.commands.economy

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object EcoCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("eco")
            .requires { it.sender.hasPermission("mistaken.admin") }
            .then(
                Commands.literal("give")
                    .then(
                        Commands.argument("target", StringArgumentType.word())
                            .suggests { _, builder ->
                                Bukkit.getOnlinePlayers().forEach { p ->
                                    if (p.name.lowercase().startsWith(builder.remainingLowerCase)) {
                                        builder.suggest(p.name)
                                    }
                                }
                                builder.buildFuture()
                            }
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val sender = context.source.sender
                                        val playerSender = sender as? Player
                                        val targetName = StringArgumentType.getString(context, "target")
                                        val amount = IntegerArgumentType.getInteger(context, "amount")
                                        
                                        val target = Bukkit.getPlayer(targetName)
                                        if (target == null) {
                                            sender.sendMessage(MessageService.getComponent(playerSender, "errors.player-not-found"))
                                            return@executes 0
                                        }
                                        
                                        val stats = plugin.statsManager.getStats(target.uniqueId)
                                        stats.coins.addAndGet(amount)
                                        
                                        sender.sendMessage(MessageService.getComponent(playerSender, "economy.give", 
                                            Placeholder.parsed("amount", amount.toString()), 
                                            Placeholder.parsed("player", target.name)))
                                            
                                        target.sendMessage(MessageService.getComponent(target, "economy.receive", 
                                            Placeholder.parsed("amount", amount.toString())))
                                        
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )
            .then(
                Commands.literal("take")
                    .then(
                        Commands.argument("target", StringArgumentType.word())
                            .suggests { _, builder ->
                                Bukkit.getOnlinePlayers().forEach { p ->
                                    if (p.name.lowercase().startsWith(builder.remainingLowerCase)) {
                                        builder.suggest(p.name)
                                    }
                                }
                                builder.buildFuture()
                            }
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val sender = context.source.sender
                                        val playerSender = sender as? Player
                                        val targetName = StringArgumentType.getString(context, "target")
                                        val amount = IntegerArgumentType.getInteger(context, "amount")
                                        
                                        val target = Bukkit.getPlayer(targetName)
                                        if (target == null) {
                                            sender.sendMessage(MessageService.getComponent(playerSender, "errors.player-not-found"))
                                            return@executes 0
                                        }
                                        
                                        val stats = plugin.statsManager.getStats(target.uniqueId)
                                        stats.coins.addAndGet(-amount)
                                        
                                        sender.sendMessage(MessageService.getComponent(playerSender, "economy.take", 
                                            Placeholder.parsed("amount", amount.toString()), 
                                            Placeholder.parsed("player", target.name)))
                                        
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )
            .then(
                Commands.literal("set")
                    .then(
                        Commands.argument("target", StringArgumentType.word())
                            .suggests { _, builder ->
                                Bukkit.getOnlinePlayers().forEach { p ->
                                    if (p.name.lowercase().startsWith(builder.remainingLowerCase)) {
                                        builder.suggest(p.name)
                                    }
                                }
                                builder.buildFuture()
                            }
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes { context ->
                                        val sender = context.source.sender
                                        val playerSender = sender as? Player
                                        val targetName = StringArgumentType.getString(context, "target")
                                        val amount = IntegerArgumentType.getInteger(context, "amount")
                                        
                                        val target = Bukkit.getPlayer(targetName)
                                        if (target == null) {
                                            sender.sendMessage(MessageService.getComponent(playerSender, "errors.player-not-found"))
                                            return@executes 0
                                        }
                                        
                                        val stats = plugin.statsManager.getStats(target.uniqueId)
                                        stats.coins.set(amount)
                                        
                                        sender.sendMessage(MessageService.getComponent(playerSender, "economy.set", 
                                            Placeholder.parsed("amount", amount.toString()), 
                                            Placeholder.parsed("player", target.name)))
                                        
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )
            .build()
    }
}
