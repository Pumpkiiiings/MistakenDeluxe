package liric.mistaken.scripting.adapter

import liric.mistaken.scripting.api.ScriptEntity
import liric.mistaken.scripting.api.ScriptLocation
import org.bukkit.entity.Entity
import org.bukkit.util.Vector

open class BukkitEntityAdapter(
    protected val entity: Entity
) : ScriptEntity {

    override fun uuid(): String = entity.uniqueId.toString()
    override fun name(): String = entity.name
    override fun is_valid(): Boolean = entity.isValid && !entity.isDead

    override fun location(): ScriptLocation {
        return BukkitLocationAdapter(entity.location)
    }

    override fun teleport(location: ScriptLocation) {
        if (location is BukkitLocationAdapter) {
            entity.teleportAsync(location.getBukkitLocation())
        }
    }

    override fun velocity_add(x: Double, y: Double, z: Double) {
        entity.velocity = entity.velocity.add(Vector(x, y, z))
    }
    
    override fun world(): liric.mistaken.scripting.api.ScriptWorld {
        return BukkitWorldAdapter(entity.world)
    }

    override fun remove() {
        entity.remove()
    }
    
    internal fun getBukkitEntity(): Entity = entity
}

