package liric.mistaken.listeners.supervivientes

import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 *[LIRIC-MISTAKEN 2.0]
 * SupervivienteGeneralListener: Protección de inventario optimizada.
 * FIX: Llamadas seguras (?.) garantizan 0 coste de CPU en el LOBBY.
 */
class SupervivienteGeneralListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // 1. Filtro O(1) rápido y seguro
        if (plugin.supervivienteManager?.esSupervivienteActivo(player) != true) return

        // 2. Bloqueo de Atajos Tácticos (Teclas 1-9 sobre un ítem o Swap)
        if (event.click == ClickType.NUMBER_KEY || event.click == ClickType.SWAP_OFFHAND) {
            event.isCancelled = true
            return
        }

        // 3. Bloqueo de Armadura y Offhand
        if (event.slotType == InventoryType.SlotType.ARMOR || event.rawSlot == 45) {
            event.isCancelled = true
            return
        }

        // 4. Bloqueo de la Hotbar (Slots 0 al 3)
        val clickedInv = event.clickedInventory
        if (clickedInv != null && clickedInv.type == InventoryType.PLAYER) {
            if (event.slot in 0..3) {
                event.isCancelled = true
                return
            }
        }

        // 5. Bloqueo de Shift+Click
        if (event.click.isShiftClick) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSwapHand(event: PlayerSwapHandItemsEvent) {
        if (plugin.supervivienteManager?.esSupervivienteActivo(event.player) == true) {
            event.isCancelled = true
        }
    }
}
