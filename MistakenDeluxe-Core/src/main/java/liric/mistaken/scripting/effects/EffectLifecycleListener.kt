package liric.mistaken.scripting.effects

import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Listener que limpia efectos automáticamente cuando un jugador se desconecta o muere.
 * También ejecuta cleanup periódico de handles muertos.
 */
class EffectLifecycleListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        EffectRegistry.stopAll(uuid)
        liric.mistaken.roles.killers.triggers.traps.WorldTrapRegistry.cleanupByOwner(uuid)
        liric.mistaken.scripting.effects.gameplay.PlayerStateRegistry.clearAllForPlayer(uuid)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val uuid = event.player.uniqueId
        EffectRegistry.stopAll(uuid)
        liric.mistaken.roles.killers.triggers.traps.WorldTrapRegistry.cleanupByOwner(uuid)
        liric.mistaken.scripting.effects.gameplay.PlayerStateRegistry.clearAllForPlayer(uuid)
    }
}
