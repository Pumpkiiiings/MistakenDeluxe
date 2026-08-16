package liric.mistaken.game.managers.visual

import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import pumpking.lib.scoreboard.ScoreboardManager as PumpkingScoreboardManager

class VisualUpdateService(private val plugin: Mistaken) {

    private var syncTask: BukkitTask? = null
    private var asyncTask: BukkitTask? = null
    
    private var syncTicks = 0L
    private var asyncTicks = 0L

    fun start() {
        // --- 1. Loop Asíncrono para Scoreboards (2 o 10 ticks) ---
        // Scoreboards no modifican entidades, envían paquetes y calculan placeholders, ideal para Async
        val scoreboardInterval = if (PumpkingScoreboardManager.supportsAnimations()) 2L else 10L
        asyncTask = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            asyncTicks += scoreboardInterval
            for (player in Bukkit.getOnlinePlayers()) {
                plugin.scoreboardManager.updatePlayer(player)
            }
        }, 20L, scoreboardInterval)

        // --- 2. Loop Síncrono para Entidades Visuales (10 y 20 ticks) ---
        // Lo corremos cada 10 ticks. NameTag usa 10, ObserverHUD usa 20.
        syncTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
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
