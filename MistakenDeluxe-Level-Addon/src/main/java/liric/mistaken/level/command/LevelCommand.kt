package liric.mistaken.level.command

import liric.mistaken.level.LevelAddonPlugin
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import net.kyori.adventure.text.minimessage.MiniMessage
import liric.mistaken.level.gui.ProgressionMenu

class LevelCommand(private val plugin: LevelAddonPlugin) : BasicCommand {

    private val mm = MiniMessage.miniMessage()

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        if (sender !is Player) {
            sender.sendMessage(mm.deserialize("<red>This command is for players only.</red>"))
            return
        }

        if (args.isEmpty()) {
            val gui = liric.mistaken.level.gui.ProgressionMenu(plugin)
            gui.open(sender)
            return
        }
    }
}
