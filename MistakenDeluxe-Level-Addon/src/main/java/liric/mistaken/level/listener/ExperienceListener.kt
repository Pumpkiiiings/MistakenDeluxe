package liric.mistaken.level.listener

import liric.mistaken.api.level.event.PlayerLevelUpEvent
import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import liric.mistaken.api.events.MistakenDeathEvent
import net.kyori.adventure.text.minimessage.MiniMessage

class ExperienceListener(private val plugin: LevelAddonPlugin) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAsyncPreLogin(event: AsyncPlayerPreLoginEvent) {
        val uuid = event.uniqueId
        
        plugin.manager.loadPlayer(uuid)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        plugin.manager.savePlayer(uuid)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerLevelUp(event: PlayerLevelUpEvent) {
        
        
        plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>Player ${event.player.name} leveled up from ${event.oldLevel} to ${event.newLevel}!</gray>"))
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onMistakenDeath(event: MistakenDeathEvent) {
        liric.mistaken.level.manager.MatchStatsTracker.addStat(event.killer.uniqueId, "kill")
    }

    @EventHandler
    fun onGeneratorFix(event: liric.mistaken.api.events.MistakenGeneratorFixEvent) {
        liric.mistaken.level.manager.MatchStatsTracker.addStat(event.player.uniqueId, "generators")
    }

    @EventHandler
    fun onSurvivorHeal(event: liric.mistaken.api.events.MistakenSurvivorHealEvent) {
        liric.mistaken.level.manager.MatchStatsTracker.addStat(event.healer.uniqueId, "heals")
    }
    
    @EventHandler
    fun onGameEnd(event: liric.mistaken.api.events.MistakenGameEndEvent) {
        val allUuids = mutableSetOf<java.util.UUID>()
        allUuids.addAll(event.session.killersUUIDs)
        allUuids.addAll(event.session.survivorsUUIDs)
        allUuids.addAll(event.session.spectatorsUUIDs)
        
        if (!event.killerWon) {
            for (uuid in allUuids) {
                val player = org.bukkit.Bukkit.getPlayer(uuid)
                if (player != null && !event.session.isKiller(uuid) && player.gameMode != org.bukkit.GameMode.SPECTATOR) {
                    liric.mistaken.level.manager.MatchStatsTracker.addStat(uuid, "escapes")
                }
            }
        }
        
        val prefix = plugin.messagesConfig.getString("prefix", "<gradient:#8e2de2:#4a00e0>[Level]</gradient> ")!!
        
        for ((uuid, playerStats) in liric.mistaken.level.manager.MatchStatsTracker.stats) {
            val player = org.bukkit.Bukkit.getPlayer(uuid) ?: continue
            var totalXp = 0
            val msgLines = mutableListOf<String>()
            
            val headerMsg = plugin.messagesConfig.getString("xp-breakdown.header", "<prefix><green>Match XP Breakdown:")!!
            msgLines.add(headerMsg.replace("<prefix>", prefix))
            
            val lineMsg = plugin.messagesConfig.getString("xp-breakdown.line", "<gray> - %count% %type%: <green>+%gained% XP")!!
            
            for ((type, count) in playerStats) {
                val xpForType = plugin.xpSourcesConfig.getXpForSource(type)
                if (xpForType > 0) {
                    val gained = (xpForType * count).toInt()
                    totalXp += gained
                    msgLines.add(lineMsg.replace("%count%", count.toString()).replace("%type%", type).replace("%gained%", gained.toString()))
                }
            }
            
            val boosterMultiplier = plugin.boosterManager.getMultiplier(uuid)
            if (boosterMultiplier > 1.0) {
                val extraXp = (totalXp * (boosterMultiplier - 1)).toInt()
                totalXp = (totalXp * boosterMultiplier).toInt()
                val boosterMsg = plugin.messagesConfig.getString("xp-breakdown.booster", "<gray> - Booster (%multiplier%x): <green>+%extra% XP")!!
                msgLines.add(boosterMsg.replace("%multiplier%", boosterMultiplier.toString()).replace("%extra%", extraXp.toString()))
            }
            
            val totalMsg = plugin.messagesConfig.getString("xp-breakdown.total", "<gray> <b>Total:</b> <green>+%total% XP")!!
            msgLines.add(totalMsg.replace("%total%", totalXp.toString()))
            
            if (totalXp > 0) {
                plugin.manager.addExperience(uuid, totalXp.toLong())
                for (line in msgLines) {
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line))
                }
            }
        }
        
        liric.mistaken.level.manager.MatchStatsTracker.clear()
    }
}
