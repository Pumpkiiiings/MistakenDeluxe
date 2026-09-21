package liric.mistaken.scripting.api

/**
 * Contrato para eventos aislados en Lua.
 * Representa el evento base que un script puede recibir.
 */
interface ScriptEvent {
    fun event_name(): String
}

interface ScriptDamageEvent : ScriptEvent {
    fun victim(): ScriptEntity
    fun attacker(): ScriptEntity?
    fun original_damage(): Double
    fun damage(): Double
    fun set_damage(amount: Double)
    fun cancel()
    fun is_cancelled(): Boolean
}

