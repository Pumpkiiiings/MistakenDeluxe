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
        val xp = plugin.xpSourcesConfig.getXpForSource("kill")
        if (xp > 0) {
            plugin.manager.addExperience(event.killer.uniqueId, xp)
            val prefix = plugin.messagesConfig.getString("prefix", "<gradient:#8e2de2:#4a00e0>[Level]</gradient> ")!!
            val msgRaw = plugin.messagesConfig.getString("messages.xp-gain", "<prefix><green>+ %amount% XP")!!
            val msg = msgRaw.replace("<prefix>", prefix).replace("%amount%", xp.toString())
            event.killer.sendMessage(MiniMessage.miniMessage().deserialize(msg))
        }
    }
}
