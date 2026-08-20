package liric.mistaken.scripting.api

/**
 * Contrato seguro para representar una ubicaciÃ³n en el mundo.
 * No expone org.bukkit.Location.
 */
interface ScriptLocation {
    fun x(): Double
    fun y(): Double
    fun z(): Double
    fun yaw(): Float
    fun pitch(): Float
    fun world_name(): String
    
    fun distance(other: ScriptLocation): Double
    fun distance_squared(other: ScriptLocation): Double
    fun add(x: Double, y: Double, z: Double): ScriptLocation
    fun clone(): ScriptLocation
}

