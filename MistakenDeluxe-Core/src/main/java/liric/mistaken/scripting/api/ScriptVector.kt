package liric.mistaken.scripting.api

/**
 * Contrato seguro para representar un vector (dirección o velocidad) en el mundo.
 * No expone org.bukkit.util.Vector directamente a Lua.
 */
interface ScriptVector {
    fun x(): Double
    fun y(): Double
    fun z(): Double
    
    fun set_x(value: Double): ScriptVector
    fun set_y(value: Double): ScriptVector
    fun set_z(value: Double): ScriptVector
    
    fun rotate_y(angle: Double): ScriptVector
    fun normalize(): ScriptVector
    fun multiply(scalar: Double): ScriptVector
    fun add(x: Double, y: Double, z: Double): ScriptVector
    
    fun clone(): ScriptVector
}
