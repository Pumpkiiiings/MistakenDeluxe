package liric.mistaken.utils.misc

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.function.Consumer

object HitboxVisualizer {

    // 🔥 INTERRUPTOR GLOBAL: Se controla desde el comando /hitbox
    var isEnabled = false

    fun toggle(): Boolean {
        isEnabled = !isEnabled
        return isEnabled
    }

    /**
     * Crea una Hitbox 3D usando BlockDisplays (Cristal tintado).
     * Ideal para habilidades continuas o proyectiles (debes borrarla manualmente con hitbox.remove()).
     *
     * @param loc Ubicación inicial.
     * @param x, y, z El radio de expansión (Los mismos números de getNearbyEntities).
     * @param mat El color del cristal.
     * @return El BlockDisplay creado.
     */
    fun createHitbox(loc: Location, x: Double, y: Double, z: Double, mat: Material = Material.LIME_STAINED_GLASS): BlockDisplay? {
        if (!isEnabled) return null

        return loc.world.spawn(loc, BlockDisplay::class.java) { display ->
            display.block = mat.createBlockData()
            display.isPersistent = false
            display.setGravity(false)
            display.isGlowing = true // Para que se vea a través de las paredes

            // getNearbyEntities expande hacia ambos lados, por lo que el tamaño real es el doble.
            val sizeX = (x * 2).toFloat()
            val sizeY = (y * 2).toFloat()
            val sizeZ = (z * 2).toFloat()

            // Para que la caja quede perfectamente centrada en el jugador/proyectil
            val translation = Vector3f(-x.toFloat(), -y.toFloat(), -z.toFloat())
            val scale = Vector3f(sizeX, sizeY, sizeZ)

            display.transformation = Transformation(translation, Quaternionf(), scale, Quaternionf())

            // Interpolación para que se mueva suavemente sin tirones
            display.teleportDuration = 1
            display.interpolationDuration = 1
        }
    }

    /**
     * Dibuja una Hitbox estática que SE BORRA SOLA después de X ticks.
     * Ideal para explosiones, golpes melee en área, o trampas temporales.
     *
     * @param plugin Instancia principal del plugin (para el scheduler).
     * @param ticks Tiempo que durará visible la caja.
     */
    fun drawInstantHitbox(plugin: Plugin, loc: Location, x: Double, y: Double, z: Double, ticks: Long, mat: Material = Material.RED_STAINED_GLASS) {
        if (!isEnabled) return

        val hitbox = createHitbox(loc, x, y, z, mat) ?: return

        // Se auto-destruye después del tiempo indicado
        plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
            if (hitbox.isValid) hitbox.remove()
        }, ticks)
    }
}