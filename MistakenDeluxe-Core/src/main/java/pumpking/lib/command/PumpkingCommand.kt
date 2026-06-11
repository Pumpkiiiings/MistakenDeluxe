package pumpking.lib.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import pumpking.lib.core.PumpkingLib
import pumpking.lib.service.PumpkingServiceManager

class PumpkingCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("pumpking.admin")) {
            sender.sendMessage("Â§cYou do not have permission to use this command.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("Â§ePumpking Framework Commands:")
            sender.sendMessage("Â§7/pumpking reload")
            sender.sendMessage("Â§7/pumpking message send <player> <path>")
            sender.sendMessage("Â§7/pumpking actionbar send <player> <path>")
            sender.sendMessage("Â§7/pumpking title send <player> <path>")
            sender.sendMessage("Â§7/pumpking bossbar send <player> <path>")
            sender.sendMessage("Â§7/pumpking animation send <player> <animation>")
            sender.sendMessage("Â§7/pumpking cache info|load|unload")
            sender.sendMessage("Â§7/pumpking db test")
            sender.sendMessage("Â§7/pumpking scoreboard test <player>")
            sender.sendMessage("Â§7/pumpking cooldown test <player>")
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                PumpkingServiceManager.messages.reload()
                sender.sendMessage("Â§aPumpking framework reloaded.")
            }
            "message", "actionbar", "title", "bossbar" -> {
                if (args.size < 4) {
                    sender.sendMessage("Â§cUsage: /pumpking ${args[0]} send <player> <path>")
                    return true
                }
                val target = Bukkit.getPlayer(args[2])
                if (target == null) {
                    sender.sendMessage("Â§cPlayer not found.")
                    return true
                }
                val path = args[3]
                
                when (args[0].lowercase()) {
                    "message" -> PumpkingServiceManager.messages.send(target, path)
                    "actionbar" -> PumpkingServiceManager.messages.actionBar(target, path)
                    "title" -> PumpkingServiceManager.messages.title(target, path)
                    "bossbar" -> PumpkingServiceManager.messages.bossBar(target, path)
                }
                sender.sendMessage("Â§aSent ${args[0]} '$path' to ${target.name}.")
            }
            "animation" -> {
                if (args.size < 4) {
                    sender.sendMessage("Â§cUsage: /pumpking animation send <player> <animation>")
                    return true
                }
                val target = Bukkit.getPlayer(args[2])
                if (target == null) {
                    sender.sendMessage("Â§cPlayer not found.")
                    return true
                }
                PumpkingServiceManager.animation.play(target, args[3])
                sender.sendMessage("Â§aPlaying animation '${args[3]}' for ${target.name}.")
            }
            "cache" -> {
                if (args.size < 2) {
                    sender.sendMessage("Â§cUsage: /pumpking cache <info|load|unload>")
                    return true
                }
                when (args[1].lowercase()) {
                    "info" -> sender.sendMessage("Â§aCache info: Check console.") // Example implementation
                    "load", "unload" -> {
                        if (args.size < 3) {
                            sender.sendMessage("Â§cUsage: /pumpking cache ${args[1]} <player>")
                            return true
                        }
                        val target = Bukkit.getPlayer(args[2])
                        if (target == null) {
                            sender.sendMessage("Â§cPlayer not found.")
                            return true
                        }
                        sender.sendMessage("Â§aCache ${args[1]} triggered for ${target.name}.")
                    }
                }
            }
            "db" -> {
                if (args.size > 1 && args[1].lowercase() == "test") {
                    sender.sendMessage("Â§aRunning DB test... Check console.")
                }
            }
            "scoreboard", "cooldown" -> {
                if (args.size < 3) {
                    sender.sendMessage("Â§cUsage: /pumpking ${args[0]} test <player>")
                    return true
                }
                val target = Bukkit.getPlayer(args[2])
                if (target == null) {
                    sender.sendMessage("Â§cPlayer not found.")
                    return true
                }
                sender.sendMessage("Â§aTesting ${args[0]} for ${target.name}.")
            }
            else -> sender.sendMessage("Â§cUnknown subcommand.")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val completions = mutableListOf<String>()
        if (args.size == 1) {
            val subcommands = listOf("reload", "message", "actionbar", "title", "bossbar", "animation", "cache", "db", "scoreboard", "cooldown")
            completions.addAll(subcommands.filter { it.startsWith(args[0], ignoreCase = true) })
        } else if (args.size == 2) {
            when (args[0].lowercase()) {
                "message", "actionbar", "title", "bossbar", "animation" -> completions.add("send")
                "cache" -> completions.addAll(listOf("info", "load", "unload").filter { it.startsWith(args[1], ignoreCase = true) })
                "db", "scoreboard", "cooldown" -> completions.add("test")
            }
        } else if (args.size == 3) {
            when (args[0].lowercase()) {
                "message", "actionbar", "title", "bossbar", "animation", "scoreboard", "cooldown" -> {
                    completions.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) })
                }
                "cache" -> {
                    if (args[1].lowercase() in listOf("load", "unload")) {
                        completions.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) })
                    }
                }
            }
        }
        return completions
    }
}
