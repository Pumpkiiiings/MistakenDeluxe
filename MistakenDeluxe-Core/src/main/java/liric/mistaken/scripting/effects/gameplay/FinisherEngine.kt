package liric.mistaken.scripting.effects.gameplay

import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Motor de finishers centralizado.
 * Escucha la muerte de players y despacha callbacks Lua registrados por los killers.
 * Elimina la necesidad de registrar listeners Bukkit desde Lua o duplicar la lógica de anti-spam.
 */
object FinisherEngine : Listener {

    private val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(Mistaken::class.java)

    // Registros (scriptId -> callback Lua)
    private val finisherCallbacks = ConcurrentHashMap<String, (Player) -> Unit>()
    
    // Anti-spam global (victimUuid -> timestamp)
    private val lastKillEffect = ConcurrentHashMap<java.util.UUID, Long>()

    /** Registra un callback para un killer específico. */
    fun registerCallback(scriptId: String, callback: (Player) -> Unit) {
        finisherCallbacks[scriptId] = callback
    }

    /** Limpia los callbacks de un killer (ej. reload). */
    fun unregisterCallback(scriptId: String) {
        finisherCallbacks.remove(scriptId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerKill(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        val session = plugin.sessionManager.getSession(attacker) ?: return
        if (session.isKiller(attacker.uniqueId)) {
            val scriptId = plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)
            val callback = finisherCallbacks[scriptId] ?: return
            
            // Verificamos muerte real (GameMode cambiado a SPECTATOR)
            // Se asume que el CombatManager o similar cambia a SPECTATOR tras este evento
            // o lo hacemos por vida <= 0
            if (victim.health - event.finalDamage <= 0.0 || victim.gameMode == org.bukkit.GameMode.SPECTATOR) {
                val now = System.currentTimeMillis()
                if (now - lastKillEffect.getOrDefault(victim.uniqueId, 0L) > 2000L) {
                    lastKillEffect[victim.uniqueId] = now
                    
                    // Disparamos el callback en el scheduler de la víctima por seguridad Folia
                    victim.scheduler.run(plugin, { _ ->
                        callback.invoke(victim)
                    }, null)
                }
            }
        }
    }
}
