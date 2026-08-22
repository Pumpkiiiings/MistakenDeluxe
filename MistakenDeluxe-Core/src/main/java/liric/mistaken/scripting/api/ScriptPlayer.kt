package liric.mistaken.scripting.api

/**
 * Contrato seguro para un Player.
 * Hereda de ScriptEntity.
 */
interface ScriptPlayer : ScriptEntity {
    fun is_online(): Boolean
    
    fun send_message(message: String)
    fun play_sound(sound: String, volume: Float, pitch: Float)
    fun play_particle(particle: String, amount: Int)
    
    fun health(): Double
    fun set_health(amount: Double)
    fun damage(amount: Double)
    
    fun has_permission(permission: String): Boolean
    
    fun set_scale(scale: Double)
    fun reset_scale()
    
    fun max_health(): Double
    fun is_killer(): Boolean
    fun is_survivor(): Boolean
}

