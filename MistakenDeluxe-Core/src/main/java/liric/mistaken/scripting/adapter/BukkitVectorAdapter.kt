package liric.mistaken.scripting.adapter

import liric.mistaken.scripting.api.ScriptVector
import org.bukkit.util.Vector

class BukkitVectorAdapter(
    private val vector: Vector
) : ScriptVector {

    override fun x(): Double = vector.x
    override fun y(): Double = vector.y
    override fun z(): Double = vector.z

    override fun set_x(value: Double): ScriptVector {
        vector.x = value
        return this
    }

    override fun set_y(value: Double): ScriptVector {
        vector.y = value
        return this
    }

    override fun set_z(value: Double): ScriptVector {
        vector.z = value
        return this
    }

    override fun rotate_y(angle: Double): ScriptVector {
        vector.rotateAroundY(angle)
        return this
    }

    override fun normalize(): ScriptVector {
        vector.normalize()
        return this
    }

    override fun multiply(scalar: Double): ScriptVector {
        vector.multiply(scalar)
        return this
    }

    override fun add(x: Double, y: Double, z: Double): ScriptVector {
        vector.add(Vector(x, y, z))
        return this
    }

    override fun clone(): ScriptVector {
        return BukkitVectorAdapter(vector.clone())
    }

    /**
     * Internal access to the underlying Bukkit Vector.
     */
    internal fun getBukkitVector(): Vector = vector
}
