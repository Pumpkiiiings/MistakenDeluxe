package liric.mistaken.listeners.supervivientes

import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteGeneralListener: Protección de inventario y equipo.
 * Optimizado para Paper 1.21.4 mediante filtrado de eventos de alta frecuencia.
 */
class SupervivienteGeneralListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // Validación rápida: Solo procesar si el jugador es un superviviente activo
        // El manager usa un ConcurrentHashMap interno (O(1)), lo cual es muy veloz.
        if (!plugin.supervivienteManager.esSupervivienteActivo(player)) return

        // 1. Bloqueo de slots críticos: ARMADURA y Mano Secundaria (RawSlot 45)
        // Usar slotType es más eficiente que comparar números de slots manualmente.
        if (event.slotType == InventoryType.SlotType.ARMOR || event.rawSlot == 45) {
            event.isCancelled = true
            return
        }

        // 2. Bloqueo de slots de Habilidades (0 al 3 en el Hotbar)
        val clickedInv = event.clickedInventory
        if (clickedInv != null && clickedInv.type == InventoryType.PLAYER) {
            // En Kotlin, 'in 0..3' se compila a una comparación numérica directa (muy rápida)
            if (event.slot in 0..3) {
                event.isCancelled = true
                return
            }
        }

        // 3. Bloqueo de Shift-Click (Previene mover ítems protegidos por atajos)
        if (event.click.isShiftClick) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSwapHand(event: PlayerSwapHandItemsEvent) {
        // Bloquear el cambio de ítems a la mano secundaria (tecla F)
        if (plugin.supervivienteManager.esSupervivienteActivo(event.player)) {
            event.isCancelled = true
        }
    }
}
