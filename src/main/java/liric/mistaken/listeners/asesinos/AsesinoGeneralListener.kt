package liric.mistaken.listeners.asesinos

import liric.mistaken.Mistaken
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoGeneralListener: Restricciones físicas y blindaje.
 */
class AsesinoGeneralListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onFallDamage(event: EntityDamageEvent) {
        // Chequeo extremadamente rápido para ahorrar CPU
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return

        val player = event.entity as? Player ?: return

        // Validación segura
        if (plugin.asesinoManager?.esElAsesino(player) == true) {
            event.isCancelled = true

            if (event.damage > 4.0) {
                player.playSound(player.location, Sound.ENTITY_ZOMBIE_STEP, 0.5f, 0.5f)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (plugin.asesinoManager?.esElAsesino(player) != true) return

        if (event.click == ClickType.SWAP_OFFHAND || event.click == ClickType.NUMBER_KEY) {
            event.isCancelled = true
            return
        }

        if (event.slotType == InventoryType.SlotType.ARMOR || event.rawSlot == 45) {
            event.isCancelled = true
            return
        }

        val clickedInv = event.clickedInventory
        if (clickedInv != null && clickedInv.type == InventoryType.PLAYER) {
            if (event.slot in 0..4) {
                event.isCancelled = true
                return
            }
        }

        if (event.click.isShiftClick) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHandSwap(event: PlayerSwapHandItemsEvent) {
        if (plugin.asesinoManager?.esElAsesino(event.player) == true) {
            event.isCancelled = true
        }
    }
}
