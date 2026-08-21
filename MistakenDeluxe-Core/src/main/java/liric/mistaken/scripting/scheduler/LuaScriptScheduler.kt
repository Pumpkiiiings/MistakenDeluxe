package liric.mistaken.scripting.scheduler

import liric.mistaken.scripting.api.ScriptScheduler
import liric.mistaken.roles.killers.CoreKiller

/**
 * Adaptador del Scheduler que delega las tareas a los métodos seguros de CoreKiller.
 */
class LuaScriptScheduler(
    private val coreKiller: CoreKiller
) : ScriptScheduler {

    override fun run_delayed(delayTicks: Long, taskId: String) {
        
        
        coreKiller.runGlobalDelayed(delayTicks) {
            
            
            
        }
    }

    override fun run_timer(delayTicks: Long, periodTicks: Long, taskId: String) {
        coreKiller.runGlobalTimer(delayTicks, periodTicks) {
            
        }
    }

    override fun cancel(taskId: String) {
        
    }
}
