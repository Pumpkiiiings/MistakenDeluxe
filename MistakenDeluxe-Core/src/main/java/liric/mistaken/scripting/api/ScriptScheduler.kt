package liric.mistaken.scripting.api

/**
 * Contrato del Scheduler para los scripts.
 * El motor que lo implemente debe asegurar que las tareas 
 * se aten al ciclo de vida del Killer que las registra.
 */
interface ScriptScheduler {
    /**
     * Programa una tarea para ejecutarse despuÃ©s de un retraso (en ticks).
     */
    fun run_delayed(delayTicks: Long, taskId: String)
    
    /**
     * Programa una tarea recurrente.
     */
    fun run_timer(delayTicks: Long, periodTicks: Long, taskId: String)
    
    /**
     * Cancela una tarea especÃ­fica por su ID.
     */
    fun cancel(taskId: String)
}

