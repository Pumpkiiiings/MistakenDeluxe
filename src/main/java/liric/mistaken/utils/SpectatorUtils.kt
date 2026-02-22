package liric.mistaken.utils

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * [LIRIC-MISTAKEN 2.0]
 * SpectatorUtils: Fix total para el modo espectador en 1.21.4.
 * Evita caídas al vacío, bugs de nado y falta de vuelo.
 */
object SpectatorUtils {

    /**
     * Convierte a un jugador en espectador de forma segura y blindada.
     */
    fun setSafeSpectator(player: Player) {
        if (!player.isOnline) return

        // 1. Limpiar estados físicos que buguean la cámara
        player.isSwimming = false // Fix bug de arrastrarse
        player.isGliding = false  // Fix bug de elatras
        player.setVisualFire(false)

        // Bajarlo de cualquier vehículo o soltar pasajeros
        player.vehicle?.removePassenger(player)
        player.passengers.forEach { player.removePassenger(it) }

        // 2. Cambiar Modo de Juego
        player.gameMode = GameMode.SPECTATOR

        // 3. Forzar Vuelo (La clave para no caer al vacío)
        player.allowFlight = true
        player.isFlying = true

        // 4. Matar la inercia (Reset de velocidad)
        // Esto detiene cualquier caída o empuje instantáneamente
        player.velocity = Vector(0.0, 0.0, 0.0)

        // 5. Pequeño "empujoncito" hacia arriba
        // Evita que el jugador se quede trabado dentro de un bloque al morir
        val loc = player.location.clone().add(0.0, 0.5, 0.0)
        player.teleport(loc)
    }
}
