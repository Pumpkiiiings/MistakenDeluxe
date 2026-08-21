package liric.mistaken.scripting.adapter

import liric.mistaken.scripting.api.HasLocation
import liric.mistaken.scripting.api.ScriptLocation
import org.bukkit.Location

class BukkitLocationAdapter(
    private val location: Location
) : ScriptLocation, HasLocation {

    override fun bukkitLocation(): Location = location
    
    override fun x(): Double = location.x
    override fun y(): Double = location.y
    override fun z(): Double = location.z
    override fun yaw(): Float = location.yaw
    override fun pitch(): Float = location.pitch
    override fun world_name(): String = location.world?.name ?: "unknown"
    
    override fun direction(): liric.mistaken.scripting.api.ScriptVector {
        return BukkitVectorAdapter(location.direction)
    }
    
    override fun distance(other: ScriptLocation): Double {
        val otherLoc = Location(location.world, other.x(), other.y(), other.z(), other.yaw(), other.pitch())
        return location.distance(otherLoc)
    }

    override fun distance_squared(other: ScriptLocation): Double {
        val otherLoc = Location(location.world, other.x(), other.y(), other.z(), other.yaw(), other.pitch())
        return location.distanceSquared(otherLoc)
    }

    override fun add(x: Double, y: Double, z: Double): ScriptLocation {
        val newLoc = location.clone().add(x, y, z)
        return BukkitLocationAdapter(newLoc)
    }

    override fun clone(): ScriptLocation {
        return BukkitLocationAdapter(location.clone())
    }
    
    /**
     * Solo accesible internamente por el plugin, NO expuesto a Lua.
     */
    internal fun getBukkitLocation(): Location = location
}

