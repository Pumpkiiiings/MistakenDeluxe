package liric.mistaken.scripting.api

/**
 * Representa el contrato que debe cumplir un script Lua.
 */
interface ScriptRole {
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
    fun on_melee_attack(attacker: ScriptPlayer, victim: ScriptPlayer, slot: Int)
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
