package liric.mistaken.listeners

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoListener: El disparador de habilidades reactivo.
 * Optimizado mediante cortocircuitos lógicos para no consumir CPU en interacciones irrelevantes.
 */
class AsesinoListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // 1. FILTRO DE ACCIÓN (Operación de bit/enum ultra-rápida)
        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        // 2. FILTRO DE SLOT (Solo slots 1 al 4 son habilidades)
        val player = event.player
        val slot = player.inventory.heldItemSlot
        if (slot !in 1..4) return

        // 3. FILTRO DE ESTADO DE PARTIDA (Acceso a variable en memoria)
        if (plugin.gameManager.currentState != GameState.INGAME) return

        // 4. FILTRO DE MODO DE JUEGO
        if (player.gameMode == GameMode.SPECTATOR) return

        // 5. BÚSQUEDA DEL OBJETO ASESINO (Búsqueda O(1) en ConcurrentHashMap)
        // Solo llegamos aquí si el jugador realmente está haciendo algo que parece una habilidad
        val asesino = plugin.asesinoManager.getAsesinoDelJugador(player) ?: return

        // --- DISPARO DE HABILIDAD ---

        // Cancelamos la interacción para evitar que el asesino abra cofres o use ítems vanilla
        event.isCancelled = true

        // Ejecutar la lógica de la habilidad (Polimorfismo)
        asesino.usarHabilidad(player, slot)
    }
}
