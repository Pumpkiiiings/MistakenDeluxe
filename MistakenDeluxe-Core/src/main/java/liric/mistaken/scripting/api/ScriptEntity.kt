package liric.mistaken.scripting.api

/**
 * Contrato base para entidades en Lua.
 * No expone org.bukkit.entity.Entity.
 */
interface ScriptEntity {
    fun uuid(): String
    fun name(): String
    fun is_valid(): Boolean
    
    fun location(): ScriptLocation
    fun teleport(location: ScriptLocation)
    fun velocity_add(x: Double, y: Double, z: Double)
    
    fun world(): ScriptWorld
    
    fun remove()
}

