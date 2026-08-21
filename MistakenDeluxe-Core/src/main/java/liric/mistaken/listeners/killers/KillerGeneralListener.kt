package liric.mistaken.listeners.killers

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
 *[LIRIC-MISTAKEN 2.0]
 * KillerGeneralListener: Restricciones físicas y blindaje de inventario.
 * FIX: Prevención de glitches de inventario (Swap Offhand) y optimización de chequeos.
 */
class KillerGeneralListener(private val plugin: Mistaken) : Listener {

    /**
     * Ventaja: Inmunidad al daño por caída para los Killers.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onFallDamage(event: EntityDamageEvent) {
        
        
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return

        val player = event.entity as? Player ?: return

        if (plugin.killerManager.isKiller(player)) {
            event.isCancelled = true

            
            if (event.damage > 4.0) {
                player.playSound(player.location, Sound.ENTITY_ZOMBIE_STEP, 0.5f, 0.5f)
            }
        }
    }

    /**
     * Blindaje: Evita que el killer tire o mueva su equipo.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (!plugin.killerManager.isKiller(player)) return

        
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

    /**
     * Bloquea la tecla F fuera del inventario (Swap hand)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHandSwap(event: PlayerSwapHandItemsEvent) {
        if (plugin.killerManager.isKiller(event.player)) {
            event.isCancelled = true
        }
    }
}
