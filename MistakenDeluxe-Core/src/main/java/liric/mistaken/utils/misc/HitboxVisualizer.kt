package liric.mistaken.utils.misc

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

object HitboxVisualizer {

    // FIX #17: Was a plain `var Boolean` â€” non-atomic read-modify-write.
    // The particle engine runs asynchronously and reads `isEnabled`; toggle()
    // performs a check-then-act that must be atomic to avoid data races.
    private val _isEnabled = AtomicBoolean(false)

    /** Thread-safe read of the enabled state. */
    val isEnabled: Boolean get() = _isEnabled.get()

    /** Atomically flips the enabled flag and returns the new value. */
    fun toggle(): Boolean = _isEnabled.updateAndGet { !it }

    /**
     * Crea una Hitbox 3D usando BlockDisplays (Cristal tintado).
     * Ideal para habilidades continuas o proyectiles (debes borrarla manualmente con hitbox.remove()).
     *
     * @param loc UbicaciÃ³n inicial.
     * @param x, y, z El radio de expansiÃ³n (Los mismos nÃºmeros de getNearbyEntities).
     * @param mat El color del cristal.
     * @return El BlockDisplay creado.
     */
    fun createHitbox(loc: Location, x: Double, y: Double, z: Double, mat: Material = Material.LIME_STAINED_GLASS): BlockDisplay? {
        if (!isEnabled) return null

        return liric.mistaken.packet.PacketFactory.displays.buildBlockDisplay(org.bukkit.Bukkit.getOnlinePlayers().toList(), loc) { display ->
            display.block = mat.createBlockData()
            display.isPersistent = false
            display.setGravity(false)
            display.isGlowing = true // Para que se vea a travÃ©s de las paredes

            // getNearbyEntities expande hacia ambos lados, por lo que el tamaÃ±o real es el doble.
            val sizeX = (x * 2).toFloat()
            val sizeY = (y * 2).toFloat()
            val sizeZ = (z * 2).toFloat()

            // Para que la caja quede perfectamente centrada en el jugador/proyectil
            val translation = Vector3f(-x.toFloat(), -y.toFloat(), -z.toFloat())
            val scale = Vector3f(sizeX, sizeY, sizeZ)

            display.transformation = Transformation(translation, Quaternionf(), scale, Quaternionf())

            // InterpolaciÃ³n para que se mueva suavemente sin tirones
            display.teleportDuration = 1
            display.interpolationDuration = 1
        }
    }

    /**
     * Dibuja una Hitbox estÃ¡tica que SE BORRA SOLA despuÃ©s de X ticks.
     * Ideal para explosiones, golpes melee en Ã¡rea, o trampas temporales.
     *
     * @param plugin Instancia principal del plugin (para el scheduler)
     * @param ticks Tiempo que durarÃ¡ visible la caja.
     */
    fun drawInstantHitbox(plugin: Plugin, loc: Location, x: Double, y: Double, z: Double, ticks: Long, mat: Material = Material.RED_STAINED_GLASS) {
        if (!isEnabled) return

        val hitbox = createHitbox(loc, x, y, z, mat) ?: return

        // Se auto-destruye despuÃ©s del tiempo indicado
        plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
            if (hitbox.isValid) hitbox.remove()
        }, ticks)
    }
}
