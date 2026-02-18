package liric.mistaken.listeners.asesinos

import liric.mistaken.Mistaken
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoGeneralListener: Restricciones físicas y blindaje de inventario.
 * Optimizado para Paper 1.21.4 con cortocircuitos lógicos de alto rendimiento.
 */
class AsesinoGeneralListener(private val plugin: Mistaken) : Listener {

    /**
     * Ventaja: Inmunidad al daño por caída para los Asesinos.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onFallDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return

        // Validación rápida: Si no es caída, no seguimos
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return

        // Si es asesino, anulamos el daño
        if (plugin.asesinoManager.esElAsesino(player)) {
            event.isCancelled = true

            // Efecto sutil si la caída fue considerable (> 2 bloques de daño teórico)
            if (event.damage > 4.0) {
                player.playSound(player.location, Sound.ENTITY_ZOMBIE_STEP, 0.5f, 0.5f)
            }
        }
    }

    /**
     * Blindaje: Evita que el asesino tire o mueva su equipo de habilidades.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // Si no es un asesino activo, ignoramos el evento de inmediato
        if (!plugin.asesinoManager.esElAsesino(player)) return

        // 1. Bloquear Armor Slots y Offhand (slot 45)
        if (event.slotType == InventoryType.SlotType.ARMOR || event.rawSlot == 45) {
            event.isCancelled = true
            return
        }

        // 2. Bloquear Hotbar de habilidades (Slot 0 al 4)
        val clickedInv = event.clickedInventory
        if (clickedInv != null && clickedInv.type == InventoryType.PLAYER) {
            // 'in 0..4' es una comparación numérica directa en Kotlin (Eficiente)
            if (event.slot in 0..4) {
                event.isCancelled = true
                return
            }
        }

        // 3. Bloquear Shift+Click para prevenir mover el arma a contenedores externos
        if (event.click.isShiftClick) {
            event.isCancelled = true
        }
    }

    /**
     * Bloquea la tecla F (Swap hand) para evitar glitches visuales con armas personalizadas.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHandSwap(event: PlayerSwapHandItemsEvent) {
        if (plugin.asesinoManager.esElAsesino(event.player)) {
            event.isCancelled = true
        }
    }
}
