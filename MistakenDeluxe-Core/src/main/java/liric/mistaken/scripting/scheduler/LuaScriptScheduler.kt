package liric.mistaken.scripting.scheduler

import liric.mistaken.scripting.api.ScriptScheduler
import liric.mistaken.Mistaken
import org.bukkit.Bukkit

/**
 * Scheduler para Lua que delega en el BukkitScheduler.
 */
class LuaScriptScheduler : ScriptScheduler {

    private val plugin = Mistaken.instance

    override fun run_delayed(delayTicks: Long, taskId: String) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, {
            
        }, delayTicks)
    }

    override fun run_timer(delayTicks: Long, periodTicks: Long, taskId: String) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, {
            
        }, delayTicks, periodTicks)
    }

    override fun cancel(taskId: String) {
        
    }
}
