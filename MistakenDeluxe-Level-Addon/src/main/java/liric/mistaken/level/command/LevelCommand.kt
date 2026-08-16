package liric.mistaken.level.command

import liric.mistaken.level.LevelAddonPlugin
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import net.kyori.adventure.text.minimessage.MiniMessage
import liric.mistaken.level.menu.ProgressionMenu

class LevelCommand(private val plugin: LevelAddonPlugin) : BasicCommand {

    private val mm = MiniMessage.miniMessage()

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        if (sender !is Player) {
            val prefix = plugin.messagesConfig.getString("prefix", "<gradient:#8e2de2:#4a00e0>[Level]</gradient> ")!!
            val msg = plugin.messagesConfig.getString("messages.only-players", "<red>This command is for players only.</red>")!!.replace("<prefix>", prefix)
            sender.sendMessage(mm.deserialize(msg))
            return
        }

        if (args.isEmpty()) {
            val gui = ProgressionMenu(plugin)
            gui.open(sender)
            return
        }
    }
}
