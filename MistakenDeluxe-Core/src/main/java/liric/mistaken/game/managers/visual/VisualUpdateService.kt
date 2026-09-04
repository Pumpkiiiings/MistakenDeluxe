package liric.mistaken.game.managers.visual

import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import liric.mistaken.utils.scoreboard.ScoreboardManager as PumpkingScoreboardManager

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit

class VisualUpdateService(private val plugin: Mistaken) {

    private var syncTask: ScheduledTask? = null
    private var asyncTask: ScheduledTask? = null
    
    private var syncTicks = 0L
    private var asyncTicks = 0L

    fun start() {
        
        
        val scoreboardInterval = if (PumpkingScoreboardManager.supportsAnimations()) 2L else 10L
        asyncTask = plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            asyncTicks += scoreboardInterval
            for (player in Bukkit.getOnlinePlayers()) {
                plugin.scoreboardManager.updatePlayer(player)
            }
        }, 1000L, scoreboardInterval * 50L, TimeUnit.MILLISECONDS)

        
        
        syncTask = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { _ ->
            syncTicks += 10L
            val doObserverHUD = syncTicks % 20L == 0L
            
            for (player in Bukkit.getOnlinePlayers()) {
                plugin.nameTagManager.updatePlayer(player)
                if (doObserverHUD) {
                    plugin.observerHUDManager.updatePlayer(player)
                }
            }
        }, 20L, 10L)
    }

    fun stop() {
        syncTask?.cancel()
        asyncTask?.cancel()
    }
}
