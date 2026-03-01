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
 * FIX: Optimización O(1) en chequeo de mundos y lógica PvP corregida.
 */
class AntiBlockListener(private val plugin: Mistaken) : Listener {

    // Cache de mundos ACTIVOS (Nombres exactos de mundos cargados)
    // Usamos ConcurrentSet para thread-safety en lecturas masivas
    private val activeGameWorlds = ConcurrentHashMap.newKeySet<String>()

    init {
        // Tarea periódica para limpiar mundos descargados (Garbage Collection de nombres)
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            updateWorldCache()
        }, 20L * 60, 20L * 60, java.util.concurrent.TimeUnit.SECONDS) // Cada 1 min
    }

    /**
     * Sincroniza los mundos cargados con el caché.
     * Llamado automáticamente al iniciar partida y periódicamente.
     */
    fun updateWorldCache() {
        // Añadimos el mundo del lobby si existe
        plugin.lobbyLocation?.world?.name?.let { activeGameWorlds.add(it) }

        // Añadimos los mundos de las arenas activas (Si usas ASP, el mundo se llama "template_timestamp")
        // Aquí asumimos que MapManager tiene una forma de saber qué mundos están vivos.
        // Si no, simplemente añadimos todos los mundos cargados que empiecen con nombres de arenas.

        val loadedWorlds = plugin.server.worlds.map { it.name }
        val arenaTemplates = plugin.arenaManager.getArenas().keys

        for (worldName in loadedWorlds) {
            // Si el mundo cargado coincide con una plantilla de arena (ej: "arena1_123456")
            if (arenaTemplates.any { worldName.startsWith(it) }) {
                activeGameWorlds.add(worldName)
            }
        }
    }

    // Helper para añadir un mundo manualmente al cargar la partida
    fun registerGameWorld(worldName: String) {
        activeGameWorlds.add(worldName)
    }

    fun unregisterGameWorld(worldName: String) {
        activeGameWorlds.remove(worldName)
    }

    /**
     * Verifica si un mundo está protegido.
     * Búsqueda O(1) instantánea en el Hash Set.
     */
    private fun isProtectedWorld(world: World): Boolean {
        return activeGameWorlds.contains(world.name)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCombatBypass(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return

        // Si no estamos en un mundo protegido, dejamos que Vanilla decida
        if (!isProtectedWorld(victim.world)) return

        val damager = event.damager as? Player ?: return

        // Lógica PvP de Mistaken:
        // Solo Asesino puede dañar.
        // Solo Superviviente puede ser dañado (por el asesino).
        // Asesino vs Asesino = Cancelado.
        // Superviviente vs Superviviente = Cancelado.

        val damagerIsKiller = plugin.gameManager.esAsesino(damager.uniqueId)
        val victimIsKiller = plugin.gameManager.esAsesino(victim.uniqueId)

        if (damagerIsKiller && !victimIsKiller) {
            // Asesino pegando a Humano -> PERMITIDO
            event.isCancelled = false
        } else if (!damagerIsKiller && victimIsKiller) {
            // Humano pegando a Asesino -> PERMITIDO (Empuje, Stun, etc)
            event.isCancelled = false
        } else {
            // Humano vs Humano o Asesino vs Asesino -> DENEGADO
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (isProtectedWorld(event.player.world)) {
            if (!plugin.isInEditMode(event.player)) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (isProtectedWorld(event.player.world)) {
            if (!plugin.isInEditMode(event.player)) {
                event.isCancelled = true
            }
        }
    }
}
