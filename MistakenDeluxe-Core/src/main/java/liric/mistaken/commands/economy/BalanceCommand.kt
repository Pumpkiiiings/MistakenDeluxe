package liric.mistaken.commands.economy

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.utils.color.ColorTranslator
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object BalanceCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("bal")
            .executes { context ->
                val player = context.source.sender as? Player
                if (player == null) {
                    context.source.sender.sendMessage("Solo jugadores.")
                    return@executes 0
                }
                
                val stats = plugin.statsManager.getStats(player.uniqueId)
                player.sendMessage(ColorTranslator.translate("<green>Tu balance actual: <gold>${stats.coins.get()} coins"))
                Command.SINGLE_SUCCESS
            }
            .then(
                Commands.argument("target", StringArgumentType.word())
                    .executes { context ->
                        val sender = context.source.sender
                        if (!sender.hasPermission("mistaken.admin")) {
                            sender.sendMessage(ColorTranslator.translate("<red>No tienes permiso para ver el balance de otros."))
                            return@executes 0
                        }
                        
                        val targetName = StringArgumentType.getString(context, "target")
                        val target = Bukkit.getPlayer(targetName)
                        if (target == null) {
                            sender.sendMessage(ColorTranslator.translate("<red>Jugador no encontrado."))
                            return@executes 0
                        }
                        
                        val stats = plugin.statsManager.getStats(target.uniqueId)
                        sender.sendMessage(ColorTranslator.translate("<green>Balance de ${target.name}: <gold>${stats.coins.get()} coins"))
                        Command.SINGLE_SUCCESS
                    }
            )
            .build()
    }
}
