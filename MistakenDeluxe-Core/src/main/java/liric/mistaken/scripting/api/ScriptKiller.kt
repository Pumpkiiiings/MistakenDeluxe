package liric.mistaken.scripting.api

/**
 * Contrato que representa el script de un Killer cargado en el Sandbox.
 * Permite al motor (KillerManager) notificar al script sobre el ciclo de vida y eventos
 * sin exponer la implementación de Lua.
 */
interface ScriptKiller {
    fun id(): String
    fun model_id(): String?
    
    
    fun on_load(context: ScriptContext)
    fun on_equip(player: ScriptPlayer)
    fun on_unequip(player: ScriptPlayer)
    fun on_tick()
    fun on_disable()
    
    
    fun dispatch_event(event: ScriptEvent)

    
    fun has_trigger(): Boolean
    fun on_trigger(player: ScriptPlayer, triggerId: String)
    fun on_intercept_chat(player: ScriptPlayer, message: String): String?
    fun on_kill(killer: ScriptPlayer, victim: ScriptPlayer)
}

/**
 * El contexto que se le entrega al killer al cargarse,
 * dándole acceso a los servicios autorizados (Scheduler, Log, etc.)
 */
interface ScriptContext {
    fun scheduler(): ScriptScheduler
    fun log_info(message: String)
    fun log_warning(message: String)
    fun log_error(message: String)
}
