package liric.mistaken.scripting.api

/**
 * Contrato seguro para un Mundo.
 */
interface ScriptWorld {
    fun name(): String
    fun is_day(): Boolean
    fun time(): Long
    
    fun play_sound(location: ScriptLocation, sound: String, volume: Float, pitch: Float)
    fun spawn_particle(particle: String, location: ScriptLocation, count: Int)
}

