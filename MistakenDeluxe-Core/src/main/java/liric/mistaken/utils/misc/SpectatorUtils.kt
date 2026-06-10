package liric.mistaken.utils.misc

import liric.mistaken.Mistaken
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector

/**
 * [LIRIC-MISTAKEN 2.0]
 * SpectatorUtils: Puente seguro para convertir jugadores en espectadores.
 * FIX: Integración con SpectatorManager para evitar bugs de colisión y vuelo en 1.21.4.
 */
object SpectatorUtils {

    /**
     * Convierte a un jugador en el modo espectador personalizado del plugin.
     */
    fun setSafeSpectator(player: Player) {
        if (!player.isOnline) return

        // 1. Limpiar estados físicos y visuales (Reset total)
        player.isSwimming = false
        player.isGliding = false
        player.isVisualFire = false
        player.fireTicks = 0

        // Atributos de velocidad (Reset por si venía de una clase con debuffs)
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 4.0

        // Bajarlo de cualquier vehículo o soltar pasajeros
        player.vehicle?.removePassenger(player)
        player.passengers.forEach { player.removePassenger(it) }

        // Matar la inercia (Detener movimiento brusco)
        player.velocity = Vector(0.0, 0.0, 0.0)

        // 2. Obtener el plugin y delegar al Manager
        // Usamos getPlugin para evitar errores de instancia nula
        val plugin = JavaPlugin.getPlugin(Mistaken::class.java)

        // 3. Teleport seguro hacia arriba (Async) para no quedar atrapado en el suelo
        val safeLoc = player.location.clone().add(0.0, 0.5, 0.0)

        player.teleportAsync(safeLoc).thenAccept { success ->
            if (success && player.isOnline) {
                // Activamos el modo fantasma (Invisibilidad, Vuelo, Brújula, etc.)
                plugin.spectatorManager.setCustomSpectator(player)
            }
        }
    }
}