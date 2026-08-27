package liric.mistaken.commands.economy

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.utils.color.ColorTranslator
import org.bukkit.Bukkit

object EcoCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("eco")
            .requires { it.sender.hasPermission("mistaken.admin") }
            .then(
                Commands.literal("give")
                    .then(
                        Commands.argument("target", StringArgumentType.word())
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val sender = context.source.sender
                                        val targetName = StringArgumentType.getString(context, "target")
                                        val amount = IntegerArgumentType.getInteger(context, "amount")
                                        
                                        val target = Bukkit.getPlayer(targetName)
                                        if (target == null) {
                                            sender.sendMessage(ColorTranslator.translate("<red>Jugador no encontrado."))
                                            return@executes 0
                                        }
                                        
                                        val stats = plugin.statsManager.getStats(target.uniqueId)
                                        stats.coins.addAndGet(amount)
                                        sender.sendMessage(ColorTranslator.translate("<green>Has dado $amount coins a ${target.name}."))
                                        target.sendMessage(ColorTranslator.translate("<green>Has recibido $amount coins."))
                                        
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )
            .then(
                Commands.literal("take")
                    .then(
                        Commands.argument("target", StringArgumentType.word())
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val sender = context.source.sender
                                        val targetName = StringArgumentType.getString(context, "target")
                                        val amount = IntegerArgumentType.getInteger(context, "amount")
                                        
                                        val target = Bukkit.getPlayer(targetName)
                                        if (target == null) {
                                            sender.sendMessage(ColorTranslator.translate("<red>Jugador no encontrado."))
                                            return@executes 0
                                        }
                                        
                                        val stats = plugin.statsManager.getStats(target.uniqueId)
                                        stats.coins.addAndGet(-amount)
                                        sender.sendMessage(ColorTranslator.translate("<green>Has quitado $amount coins a ${target.name}."))
                                        
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )
            .then(
                Commands.literal("set")
                    .then(
                        Commands.argument("target", StringArgumentType.word())
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes { context ->
                                        val sender = context.source.sender
                                        val targetName = StringArgumentType.getString(context, "target")
                                        val amount = IntegerArgumentType.getInteger(context, "amount")
                                        
                                        val target = Bukkit.getPlayer(targetName)
                                        if (target == null) {
                                            sender.sendMessage(ColorTranslator.translate("<red>Jugador no encontrado."))
                                            return@executes 0
                                        }
                                        
                                        val stats = plugin.statsManager.getStats(target.uniqueId)
                                        stats.coins.set(amount)
                                        sender.sendMessage(ColorTranslator.translate("<green>Has establecido los coins de ${target.name} a $amount."))
                                        
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )
            .build()
    }
}
