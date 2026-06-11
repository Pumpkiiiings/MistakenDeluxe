package liric.mistaken.level.command

import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.Bukkit
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.MiniMessage

class LevelAdminCommand(private val plugin: LevelAddonPlugin) : BasicCommand {

    private val mm = MiniMessage.miniMessage()

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        if (!sender.hasPermission("mistaken.level.admin")) {
            sender.sendMessage(mm.deserialize("<red>You do not have permission to use this command."))
            return
        }

        if (args.size < 3) {
            sender.sendMessage(mm.deserialize("<red>Usage: /leveladmin <addxp|setlevel> <player> <amount>"))
            return
        }

        val action = args[0].lowercase()
        val targetName = args[1]
        val amount = args[2].toLongOrNull() ?: return

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            sender.sendMessage(mm.deserialize("<red>Player not found."))
            return
        }

        when (action) {
            "addxp" -> {
                plugin.manager.addExperience(target.uniqueId, amount, liric.mistaken.api.level.event.PlayerExperienceGainEvent.GainReason.COMMAND)
                sender.sendMessage(mm.deserialize("<green>Added <gold>$amount XP</gold> to <yellow>${target.name}</yellow>."))
            }
            "setlevel" -> {
                plugin.manager.setLevel(target.uniqueId, amount.toInt())
                sender.sendMessage(mm.deserialize("<green>Set <yellow>${target.name}</yellow>'s level to <gold>$amount</gold>."))
            }
            else -> {
                sender.sendMessage(mm.deserialize("<red>Unknown action."))
            }
        }
    }
}
