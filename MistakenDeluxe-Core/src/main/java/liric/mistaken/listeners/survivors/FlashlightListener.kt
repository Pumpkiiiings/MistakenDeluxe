package liric.mistaken.listeners.survivors

import liric.mistaken.Mistaken
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 * [LIRIC-MISTAKEN 2.0]
 * FlashlightListener: la tecla F deja de intercambiar la segunda mano y pasa a
 * encender/apagar la linterna del survivor.
 *
 * PlayerSwapHandItemsEvent se dispara aunque ambas manos esten vacias, asi que no hace
 * falta interceptar el packet. Si canUse() dice que no (killer, lobby, muerto), el
 * evento no se cancela y el swap funciona como siempre.
 */
class FlashlightListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!plugin.flashlightManager.canUse(player)) return

        event.isCancelled = true
        plugin.flashlightManager.toggle(player)
    }
}
