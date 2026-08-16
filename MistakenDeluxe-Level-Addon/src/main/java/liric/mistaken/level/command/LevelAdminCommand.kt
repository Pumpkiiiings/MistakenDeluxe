package liric.mistaken.level.command

import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.MiniMessage
import liric.mistaken.Mistaken
import liric.mistaken.api.level.event.PlayerExperienceGainEvent

class LevelAdminCommand(private val plugin: LevelAddonPlugin) : BasicCommand {

    private val mm = MiniMessage.miniMessage()

    private fun sendMsg(sender: CommandSender, path: String, defaultMsg: String, vararg replacements: Pair<String, String>) {
        val prefix = plugin.messagesConfig.getString("prefix", "<gradient:#8e2de2:#4a00e0>[Level]</gradient> ")!!
        var msg = plugin.messagesConfig.getString(path, defaultMsg)!!.replace("<prefix>", prefix)
        for ((key, value) in replacements) {
            msg = msg.replace(key, value)
        }
        sender.sendMessage(mm.deserialize(msg))
    }

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        if (!sender.hasPermission("mistaken.level.admin")) {
            sendMsg(sender, "messages.no-permission", "<red>You do not have permission to use this command.")
            return
        }

        if (args.size < 3) {
            sendMsg(sender, "messages.admin-usage", "<red>Usage: /leveladmin <addxp|setlevel|addkills|addwins_survivor|addwins_killer|addgenerators> <player> <amount>")
            return
        }

        val action = args[0].lowercase()
        val targetName = args[1]
        val amount = args[2].toLongOrNull() ?: return

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            sendMsg(sender, "messages.player-not-found", "<red>Player not found.")
            return
        }

        when (action) {
            "addxp" -> {
                plugin.manager.addExperience(target.uniqueId, amount, PlayerExperienceGainEvent.GainReason.COMMAND)
                sendMsg(sender, "messages.admin-add-xp", "<prefix><green>Added <gold>%amount% XP</gold> to <yellow>%player%</yellow>.", "%amount%" to amount.toString(), "%player%" to target.name)
            }
            "setlevel" -> {
                plugin.manager.setLevel(target.uniqueId, amount.toInt())
                sendMsg(sender, "messages.admin-set-level", "<prefix><green>Set <yellow>%player%</yellow>'s level to <gold>%level%</gold>.", "%level%" to amount.toString(), "%player%" to target.name)
            }
            "addkills", "addwins_survivor", "addwins_killer", "addgenerators" -> {
                val mistakenCore = Bukkit.getPluginManager().getPlugin("Mistaken") as? Mistaken
                if (mistakenCore == null) {
                    sendMsg(sender, "messages.core-not-found", "<red>Core plugin not found!")
                    return
                }
                val stats = mistakenCore.statsManager.getStats(target.uniqueId)
                when (action) {
                    "addkills" -> {
                        stats.kills.addAndGet(amount.toInt())
                        sendMsg(sender, "messages.admin-add-kills", "<prefix><green>Added <gold>%amount% Kills</gold> to <yellow>%player%</yellow>.", "%amount%" to amount.toString(), "%player%" to target.name)
                    }
                    "addwins_survivor" -> {
                        stats.winsSurvivor.addAndGet(amount.toInt())
                        sendMsg(sender, "messages.admin-add-wins-survivor", "<prefix><green>Added <gold>%amount% Survivor Wins</gold> to <yellow>%player%</yellow>.", "%amount%" to amount.toString(), "%player%" to target.name)
                    }
                    "addwins_killer" -> {
                        stats.winsAssassin.addAndGet(amount.toInt())
                        sendMsg(sender, "messages.admin-add-wins-killer", "<prefix><green>Added <gold>%amount% Killer Wins</gold> to <yellow>%player%</yellow>.", "%amount%" to amount.toString(), "%player%" to target.name)
                    }
                    "addgenerators" -> {
                        stats.generatorsRepaired.addAndGet(amount.toInt())
                        sendMsg(sender, "messages.admin-add-generators", "<prefix><green>Added <gold>%amount% Generators</gold> to <yellow>%player%</yellow>.", "%amount%" to amount.toString(), "%player%" to target.name)
                    }
                }
                plugin.manager.checkLevelUp(target.uniqueId)
            }
            else -> {
                sendMsg(sender, "messages.unknown-action", "<red>Unknown action.")
            }
        }
    }
}
