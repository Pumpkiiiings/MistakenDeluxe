package liric.mistaken.listeners

import liric.mistaken.Mistaken
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * AntiBlockListener: Protección perimetral y de combate.
 * Optimizado con un sistema de caché O(1) para evitar lag en eventos de alta frecuencia.
 */
class AntiBlockListener(private val plugin: Mistaken) : Listener {

    // Usamos un ConcurrentSet para que la lectura sea ultra-rápida y segura entre hilos
    private val gameWorlds = ConcurrentHashMap.newKeySet<String>()

    init {
        updateWorldCache()
    }

    /**
     * Sincroniza los nombres de los mundos de las arenas con el caché.
     * Debe llamarse al cargar el plugin o al crear/borrar arenas.
     */
    fun updateWorldCache() {
        gameWorlds.clear()
        plugin.arenaManager.getArenas().keys.forEach { arenaId ->
            gameWorlds.add(arenaId.lowercase())
        }
        plugin.logger.info("§b[Mistaken] Protección de mundos actualizada (${gameWorlds.size} arenas).")
    }

    /**
     * Verifica si un mundo es una arena de juego.
     * Optimización: Primero busca coincidencia exacta, luego parcial (para ASP).
     */
    private fun isGameWorld(world: World): Boolean {
        val name = world.name.lowercase()
        // Búsqueda instantánea O(1)
        if (gameWorlds.contains(name)) return true

        // Búsqueda por prefijo O(n) - Solo si la arena usa mundos dinámicos de ASP
        // Se usa 'any' que corta la ejecución en cuanto encuentra el primero (short-circuit)
        return gameWorlds.any { name.contains(it) }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCombatBypass(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val damager = event.damager as? Player ?: return

        // Si no es mundo de juego, salimos de inmediato
        if (!isGameWorld(victim.world)) return

        val damagerUUID = damager.uniqueId
        val victimUUID = victim.uniqueId

        // Usamos el GameManager que tiene el set de asesinos en RAM (Acceso ultra-rápido)
        val damagerIsKiller = plugin.gameManager.esAsesino(damagerUUID)
        val victimIsKiller = plugin.gameManager.esAsesino(victimUUID)

        // El combate solo se permite si uno de los dos es el asesino
        // Si ambos son supervivientes o si no hay asesino involucrado, se cancela el daño
        if (!(damagerIsKiller || victimIsKiller)) {
            event.isCancelled = true
        } else {
            // Aseguramos que el daño sea procesado si es Asesino vs Humano
            event.isCancelled = false
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (isGameWorld(player.world)) {
            // Solo permitimos romper bloques si el staff está en modo edición
            if (!plugin.isInEditMode(player)) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        if (isGameWorld(player.world)) {
            // Solo permitimos poner bloques si el staff está en modo edición
            if (!plugin.isInEditMode(player)) {
                event.isCancelled = true
            }
        }
    }
}
