package liric.mistaken.visual.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import liric.mistaken.visual.VisualAddon
import net.kyori.adventure.text.minimessage.MiniMessage

@Suppress("UnstableApiUsage")
class VisualCommand : BasicCommand {
    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        if (!sender.hasPermission("mistaken.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>No tienes permisos.</red>"))
            return
        }
        if (args.isEmpty() || args[0].lowercase() == "reload") {
            VisualAddon.instance.reloadConfig()
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>MistakenDeluxe-Visual-Addon config reloaded!</green>"))
        } else {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Uso: /visual reload</red>"))
        }
    }
}
