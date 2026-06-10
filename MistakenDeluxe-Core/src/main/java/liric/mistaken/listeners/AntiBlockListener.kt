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
import java.util.concurrent.TimeUnit

/**
 * [LIRIC-MISTAKEN 2.0]
 * AntiBlockListener: Protección perimetral y de combate adaptada a MULTIARENA.
 * FIX: Las reglas de daño ahora se aplican por sesión individual.
 */
class AntiBlockListener(private val plugin: Mistaken) : Listener {

    private val activeGameWorlds = ConcurrentHashMap.newKeySet<String>()

    init {
        // Tarea periódica para limpiar la caché de mundos
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            updateWorldCache()
        }, 1, 1, TimeUnit.MINUTES)
    }

    fun updateWorldCache() {
        // 1. Proteger el mundo del lobby
        plugin.lobbyLocation?.world?.name?.let { activeGameWorlds.add(it) }

        // 2. Proteger mundos de sesiones activas
        val loadedWorlds = plugin.server.worlds.map { it.name }
        val arenaTemplates = plugin.arenaManager.getArenas().keys

        for (worldName in loadedWorlds) {
            // Si el mundo es una arena dinámica (ASP), lo protegemos
            if (arenaTemplates.any { worldName.startsWith(it) }) {
                activeGameWorlds.add(worldName)
            }
        }
    }

    fun registerGameWorld(worldName: String) { activeGameWorlds.add(worldName) }
    fun unregisterGameWorld(worldName: String) { activeGameWorlds.remove(worldName) }

    private fun isProtectedWorld(world: World): Boolean = activeGameWorlds.contains(world.name)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCombatBypass(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return

        // Si el mundo no es de Mistaken, no intervenimos
        if (!isProtectedWorld(victim.world)) return

        val damager = event.damager as? Player ?: return

        // 🔥 MULTIARENA: Buscamos la sesión de los involucrados
        val session = plugin.sessionManager.getSession(victim) ?: return

        // Seguridad: Si están en el mismo mundo pero en sesiones distintas (error raro de TP)
        // o si uno está en el lobby y el otro no, cancelamos daño.
        if (plugin.sessionManager.getSession(damager) != session) {
            event.isCancelled = true
            return
        }

        // Aplicamos reglas de equipo basadas en SU sesión
        val damagerIsKiller = session.esAsesino(damager.uniqueId)
        val victimIsKiller = session.esAsesino(victim.uniqueId)

        if (damagerIsKiller && !victimIsKiller) {
            // Asesino pegando a Humano -> PERMITIDO
            event.isCancelled = false
        } else if (!damagerIsKiller && victimIsKiller) {
            // Humano pegando a Asesino -> PERMITIDO (Empuje/Stun)
            event.isCancelled = false
        } else {
            // Fuego amigo (Humano vs Humano o Asesino vs Asesino) -> DENEGADO
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
