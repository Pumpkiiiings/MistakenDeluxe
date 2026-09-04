package liric.mistaken.roles.common.triggers

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.roles.killers.CoreKiller
import liric.mistaken.roles.survivors.Survivor
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot

class TriggerListener(private val plugin: Mistaken) : Listener {

    private fun getActiveRole(player: Player): Any? {
        val session = plugin.sessionManager.getSession(player) ?: return null
        if (session.currentState != GameState.INGAME) return null
        if (player.gameMode != GameMode.SURVIVAL || plugin.spectatorManager.isSpectator(player)) return null

        if (session.isKiller(player.uniqueId)) {
            val killer = plugin.killerManager.getKillerOfPlayer(player)
            return killer as? CoreKiller
        } else {
            return plugin.survivorManager.getSurvivorClass(player)
        }
    }

    private fun getTriggerRegistry(role: Any): TriggerRegistry? {
        return when (role) {
            is CoreKiller -> role.triggerRegistry
            is Survivor -> role.triggerRegistry
            else -> null
        }
    }

    private fun fireTrigger(role: Any, player: Player, triggerId: String) {
        when (role) {
            is CoreKiller -> role.onTrigger(player, triggerId)
            is Survivor -> role.onTrigger(player, triggerId)
        }
    }

    private fun handleInput(player: Player, input: InputTrigger): Boolean {
        val role = getActiveRole(player) ?: return false
        val registry = getTriggerRegistry(role) ?: return false
        
        val triggers = registry.getTriggersForInput(input)
        if (triggers.isEmpty()) return false

        var handled = false
        for (trigger in triggers) {
            if (!registry.checkCooldown(player, trigger.triggerId, trigger.cooldownSeconds)) {
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    fireTrigger(role, player, trigger.triggerId)
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
        val role = getActiveRole(player) ?: return
        
        
        
        
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
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val role = getActiveRole(player) ?: return
        val registry = getTriggerRegistry(role) ?: return
        
        val triggers = registry.getTriggersForInput(InputTrigger.CHAT_MESSAGE)
        for (trigger in triggers) {
            if (!registry.checkCooldown(player, trigger.triggerId, trigger.cooldownSeconds)) {
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    fireTrigger(role, player, trigger.triggerId)
                })
            }
        }

        
        if (role is CoreKiller) {
            val message = PlainTextComponentSerializer.plainText().serialize(event.message())
            val rewritten = role.onInterceptChat(player, message)
            if (rewritten != null) {
                event.isCancelled = true
                
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.server.onlinePlayers.forEach { p ->
                        p.sendMessage(rewritten)
                    }
                })
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKillerKill(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        if (victim.gameMode == GameMode.SPECTATOR) {
            val role = getActiveRole(attacker)
            if (role is CoreKiller) {
                role.onKill(attacker, victim)
            }
        }
    }
}
