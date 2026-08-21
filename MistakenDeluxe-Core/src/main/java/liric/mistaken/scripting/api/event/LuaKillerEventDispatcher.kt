package liric.mistaken.scripting.api.event

import liric.mistaken.Mistaken
import liric.mistaken.scripting.adapter.BukkitDamageEventAdapter
import liric.mistaken.scripting.adapter.BukkitEntityAdapter
import liric.mistaken.scripting.api.ScriptKiller
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

/**
 * Dispatcher global que intercepta eventos de Bukkit, los empaqueta 
 * en Adapters seguros (ScriptAPI) y los envÃ­a a los Lua Scripts.
 */
class LuaKillerEventDispatcher(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damager
        val victim = event.entity
        
        // 1. Si el atacante es un Killer manejado por Script
        if (attacker is Player && plugin.killerManager.isKiller(attacker)) {
            val killer = plugin.killerManager.getKillerOfPlayer(attacker)
            if (killer is liric.mistaken.scripting.adapter.LuaKillerAdapter) {
                val victimAdapter = BukkitEntityAdapter(victim)
                val attackerAdapter = BukkitEntityAdapter(attacker)
                val damageEvent = BukkitDamageEventAdapter(event, victimAdapter, attackerAdapter)
                
                killer.dispatchEvent(damageEvent)
            }
        }
        
        // 2. Si la vÃ­ctima es un Killer manejado por Script
        if (victim is Player && plugin.killerManager.isKiller(victim)) {
            val killer = plugin.killerManager.getKillerOfPlayer(victim)
            if (killer is liric.mistaken.scripting.adapter.LuaKillerAdapter) {
                val victimAdapter = BukkitEntityAdapter(victim)
                val attackerAdapter = BukkitEntityAdapter(attacker)
                val damageEvent = BukkitDamageEventAdapter(event, victimAdapter, attackerAdapter)
                
                killer.dispatchEvent(damageEvent)
            }
        }
    }
}


