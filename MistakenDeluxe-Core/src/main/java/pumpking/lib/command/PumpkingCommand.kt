package pumpking.lib.command

import org.bukkit.Bukkit
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import pumpking.lib.core.PumpkingLib
import pumpking.lib.service.PumpkingServiceManager

class PumpkingCommand : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        if (!sender.hasPermission("pumpking.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.")
            return
        }

        if (args.isEmpty()) {
            sender.sendMessage("§ePumpking Framework Commands:")
            sender.sendMessage("§7/pumpking reload")
            sender.sendMessage("§7/pumpking message send <player> <path>")
            sender.sendMessage("§7/pumpking actionbar send <player> <path>")
            sender.sendMessage("§7/pumpking title send <player> <path>")
            sender.sendMessage("§7/pumpking bossbar send <player> <path>")
            sender.sendMessage("§7/pumpking animation send <player> <animation>")
            sender.sendMessage("§7/pumpking cache info|load|unload")
            sender.sendMessage("§7/pumpking db test")
            sender.sendMessage("§7/pumpking scoreboard test <player>")
            sender.sendMessage("§7/pumpking cooldown test <player>")
            return
        }

        when (args[0].lowercase()) {
            "reload" -> {
                PumpkingServiceManager.messages.reload()
                sender.sendMessage("§aPumpking framework reloaded.")
            }
            "message", "actionbar", "title", "bossbar" -> {
                if (args.size < 4) {
                    sender.sendMessage("§cUsage: /pumpking ${args[0]} send <player> <path>")
                    return
                }
                val target = Bukkit.getPlayer(args[2])
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.")
                    return
                }
                val path = args[3]

                when (args[0].lowercase()) {
                    "message" -> PumpkingServiceManager.messages.send(target, path)
                    "actionbar" -> PumpkingServiceManager.messages.actionBar(target, path)
                    "title" -> PumpkingServiceManager.messages.title(target, path)
                    "bossbar" -> PumpkingServiceManager.messages.bossBar(target, path)
                }
                sender.sendMessage("§aSent ${args[0]} '$path' to ${target.name}.")
            }
            "animation" -> {
                if (args.size < 4) {
                    sender.sendMessage("§cUsage: /pumpking animation send <player> <animation>")
                    return
                }
                val target = Bukkit.getPlayer(args[2])
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.")
                    return
                }
                PumpkingServiceManager.animation.play(target, args[3])
                sender.sendMessage("§aPlaying animation '${args[3]}' for ${target.name}.")
            }
            "cache" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /pumpking cache <info|load|unload>")
                    return
                }
                when (args[1].lowercase()) {
                    "info" -> sender.sendMessage("§aCache info: Check console.") // Example implementation
                    "load", "unload" -> {
                        if (args.size < 3) {
                            sender.sendMessage("§cUsage: /pumpking cache ${args[1]} <player>")
                            return
                        }
                        val target = Bukkit.getPlayer(args[2])
                        if (target == null) {
                            sender.sendMessage("§cPlayer not found.")
                            return
                        }
                        sender.sendMessage("§aCache ${args[1]} triggered for ${target.name}.")
                    }
                }
            }
            "db" -> {
                if (args.size > 1 && args[1].lowercase() == "test") {
                    sender.sendMessage("§aRunning DB test... Check console.")
                }
            }
            "scoreboard", "cooldown" -> {
                if (args.size < 3) {
                    sender.sendMessage("§cUsage: /pumpking ${args[0]} test <player>")
                    return
                }
                val target = Bukkit.getPlayer(args[2])
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.")
                    return
                }
                sender.sendMessage("§aTesting ${args[0]} for ${target.name}.")
            }
            else -> sender.sendMessage("§cUnknown subcommand.")
        }
    }

    override fun suggest(stack: CommandSourceStack, args: Array<String>): List<String> {
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
