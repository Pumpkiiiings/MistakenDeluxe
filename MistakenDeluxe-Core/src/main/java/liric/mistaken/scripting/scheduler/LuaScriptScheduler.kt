package liric.mistaken.scripting.scheduler

import liric.mistaken.scripting.api.ScriptScheduler
import liric.mistaken.roles.killers.CoreKiller

/**
 * Adaptador del Scheduler que delega las tareas a los mÃ©todos seguros de CoreKiller.
 */
class LuaScriptScheduler(
    private val coreKiller: CoreKiller
) : ScriptScheduler {

    override fun run_delayed(delayTicks: Long, taskId: String) {
        // Por ahora usamos GlobalDelayed, pero idealmente 
        // deberÃ­a atarse al Player si es una tarea local.
        coreKiller.runGlobalDelayed(delayTicks) {
            // Callback placeholder (necesitarÃ­amos mantener un registro de lambdas 
            // si Lua pasa un callback, pero en este contrato solo usamos IDs de tareas 
            // o se pasarÃ­a un Runnable desde el adapter interno).
        }
    }

    override fun run_timer(delayTicks: Long, periodTicks: Long, taskId: String) {
        coreKiller.runGlobalTimer(delayTicks, periodTicks) {
            // Placeholder callback
        }
    }

    override fun cancel(taskId: String) {
        // TODO: Implementar cancelaciÃ³n especÃ­fica por taskId en CoreKiller
    }
}


