package liric.mistaken.roles.killers.triggers

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.roles.killers.CoreKiller
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot

class TriggerListener(private val plugin: Mistaken) : Listener {

    private fun getActiveKiller(player: Player): CoreKiller? {
        val session = plugin.sessionManager.getSession(player) ?: return null
        if (session.currentState != GameState.INGAME) return null
        if (!session.isKiller(player.uniqueId)) return null
        if (player.gameMode != GameMode.SURVIVAL || plugin.spectatorManager.isSpectator(player)) return null

        val killer = plugin.killerManager.getKillerOfPlayer(player) ?: return null
        return killer as? CoreKiller
    }

    private fun handleInput(player: Player, input: InputTrigger): Boolean {
        val killer = getActiveKiller(player) ?: return false
        
        val triggers = killer.triggerRegistry.getTriggersForInput(input)
        if (triggers.isEmpty()) return false

        var handled = false
        for (trigger in triggers) {
            if (!killer.triggerRegistry.checkCooldown(player, trigger.triggerId, trigger.cooldownSeconds)) {
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    killer.onTrigger(player, trigger.triggerId)
                })
                handled = true
            } else {
                
                handled = true
            }
        }
        return handled
    }

    /**
     * Reemplaza el PlayerInteractEvent para skills 1-4.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onUseAbility(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.action.isRightClick) return

        val player = event.player
        val killer = getActiveKiller(player) ?: return
        
        
        
        
        val slot = player.inventory.heldItemSlot
        val input = when (slot) {
            0 -> InputTrigger.SLOT_1 
            
            
            1 -> InputTrigger.SLOT_1
            2 -> InputTrigger.SLOT_2
            3 -> InputTrigger.SLOT_3
            4 -> InputTrigger.SLOT_4
            else -> null
        }
        
        if (input != null) {
            if (handleInput(player, input)) {
                event.isCancelled = true
                plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })
            } else {
                
                
                
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        if (handleInput(event.player, InputTrigger.SWAP_HANDS)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDropItem(event: PlayerDropItemEvent) {
        if (handleInput(event.player, InputTrigger.DROP_ITEM)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        val killer = getActiveKiller(attacker) ?: return
        
        if (liric.mistaken.scripting.effects.gameplay.GameplayFunctions.isValidTarget(attacker, victim)) {
            handleInput(attacker, InputTrigger.ATTACK)
            
        }
    }


    @EventHandler(priority = EventPriority.HIGH)
    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        if (event.isSneaking) { 
            handleInput(event.player, InputTrigger.SNEAK_TOGGLE)
            
            
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val killer = getActiveKiller(player) ?: return

        
        val triggers = killer.triggerRegistry.getTriggersForInput(InputTrigger.CHAT_MESSAGE)
        for (trigger in triggers) {
            if (!killer.triggerRegistry.checkCooldown(player, trigger.triggerId, trigger.cooldownSeconds)) {
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    killer.onTrigger(player, trigger.triggerId)
                })
            }
        }

        
        val rewritten = killer.onInterceptChat(player, event.message)
        if (rewritten != null) {
            event.isCancelled = true
            
            
            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.onlinePlayers.forEach { p ->
                    p.sendMessage(rewritten)
                }
            })
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKillerKill(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        if (victim.gameMode == GameMode.SPECTATOR) {
            val killer = getActiveKiller(attacker) ?: return
            
            
            killer.onKill(attacker, victim)
        }
    }
}
