package liric.mistaken.preview.commands

import liric.mistaken.preview.PreviewAddon
import liric.mistaken.preview.menus.CharacterSelectorMenu
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class PreviewCommand : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true
        if (!sender.hasPermission("mistaken.admin")) return true

        if (args.isEmpty()) {
            sender.sendMessage("§cUsage: /preview <setup|enter>")
            return true
        }

        when (args[0].lowercase()) {
            "setup" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /preview setup <spawn|camera>")
                    return true
                }
                val loc = sender.location
                val config = PreviewAddon.instance.config
                if (args[1].lowercase() == "spawn") {
                    config.set("locations.spawn", loc)
                    PreviewAddon.instance.saveConfig()
                    sender.sendMessage("§aSpawn location set!")
                } else if (args[1].lowercase() == "camera") {
                    config.set("locations.camera", loc)
                    PreviewAddon.instance.saveConfig()
                    sender.sendMessage("§aCamera location set!")
                }
            }
            "enter" -> {
                CharacterSelectorMenu.openMenu(sender)
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("mistaken.admin")) return emptyList()
        if (args.size == 1) {
            return listOf("setup", "enter").filter { it.startsWith(args[0].lowercase()) }
        } else if (args.size == 2 && args[0].lowercase() == "setup") {
            return listOf("spawn", "camera").filter { it.startsWith(args[1].lowercase()) }
        }
        return emptyList()
    }
}
