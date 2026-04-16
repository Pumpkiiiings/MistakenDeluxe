package liric.mistaken.utils.misc

import liric.mistaken.Mistaken
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector

/**
 * [LIRIC-MISTAKEN 2.0]
 * SpectatorUtils: Puente seguro para conversiones a espectador.
 * FIX: Adaptación total a EntitySchedulers (Folia).
 */
object SpectatorUtils {

    fun setSafeSpectator(player: Player) {
        if (!player.isOnline) return
        val plugin = JavaPlugin.getPlugin(Mistaken::class.java)

        // 1. Detener acciones asíncronas / físicas forzando en el hilo del jugador
        player.scheduler.run(plugin, { _ ->
            player.isSwimming = false
            player.isGliding = false
            player.isVisualFire = false
            player.fireTicks = 0

            player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
            player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 4.0

            player.vehicle?.removePassenger(player)
            player.passengers.forEach { player.removePassenger(it) }

            player.velocity = Vector(0.0, 0.0, 0.0)

            // 2. Ejecutar Teleport Async para desengancharlo del suelo seguro
            val safeLoc = player.location.clone().add(0.0, 0.5, 0.0)
            player.teleportAsync(safeLoc).thenAccept { success ->
                if (success && player.isOnline) {
                    plugin.spectatorManager?.setCustomSpectator(player)
                }
            }
        }, null)
    }
}