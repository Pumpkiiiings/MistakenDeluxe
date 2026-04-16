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
 * AntiBlockListener: Protección perimetral adaptada a MULTIARENA.
 */
class AntiBlockListener(private val plugin: Mistaken) : Listener {

    private val activeGameWorlds = ConcurrentHashMap.newKeySet<String>()

    init {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            updateWorldCache()
        }, 1, 1, TimeUnit.MINUTES)
    }

    fun updateWorldCache() {
        plugin.lobbyLocation?.world?.name?.let { activeGameWorlds.add(it) }

        val loadedWorlds = plugin.server.worlds.map { it.name }
        // Safe call sobre arenaManager por si estamos en NETWORK_LOBBY
        val arenaTemplates = plugin.arenaManager?.getArenas()?.keys ?: emptySet()

        for (worldName in loadedWorlds) {
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

        if (!isProtectedWorld(victim.world)) return
        val damager = event.damager as? Player ?: return

        // 🔥 MULTIARENA (Safe call)
        val sessionManager = plugin.sessionManager ?: return
        val session = sessionManager.getSession(victim) ?: return

        if (sessionManager.getSession(damager) != session) {
            event.isCancelled = true
            return
        }

        val damagerIsKiller = session.esAsesino(damager.uniqueId)
        val victimIsKiller = session.esAsesino(victim.uniqueId)

        if (damagerIsKiller && !victimIsKiller) {
            event.isCancelled = false
        } else if (!damagerIsKiller && victimIsKiller) {
            event.isCancelled = false
        } else {
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
