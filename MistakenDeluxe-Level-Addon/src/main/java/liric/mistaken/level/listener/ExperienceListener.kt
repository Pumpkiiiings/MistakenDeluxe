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

class ExperienceListener(private val plugin: LevelAddonPlugin) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAsyncPreLogin(event: AsyncPlayerPreLoginEvent) {
        val uuid = event.uniqueId
        // Pre-load data so it's ready when they join
        plugin.manager.loadPlayer(uuid)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        plugin.manager.savePlayer(uuid)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerLevelUp(event: PlayerLevelUpEvent) {
        // Here we could parse rewards.yml and give rewards
        // We'll leave it as a TODO for the full implementation, but the hook is here.
        plugin.logger.info("Player ${event.player.name} leveled up from ${event.oldLevel} to ${event.newLevel}!")
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onMistakenDeath(event: MistakenDeathEvent) {
        val xp = plugin.xpSourcesConfig.getXpForSource("kill")
        if (xp > 0) {
            plugin.manager.addExperience(event.killer.uniqueId, xp)
            event.killer.sendMessage(plugin.logger.name + " gained $xp XP for a kill!") // This can be localized
        }
    }
}
