package liric.mistaken.listeners.interactables

import liric.mistaken.Mistaken
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import liric.mistaken.config.engine.core.MessageService
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class KeypadListener(private val plugin: Mistaken) : Listener {

    
    private val activeTyping = ConcurrentHashMap<UUID, Pair<Location, String>>()

    private fun cancelWord(player: Player): String =
        MessageService.getRawString(player, "listeners.keypad.cancel_word", "cancelar", "messages")

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.type != Material.AMETHYST_BLOCK) return

        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return

        if (session.isKiller(player.uniqueId)) {
            player.sendMessage(MessageService.getComponent(player, "listeners.keypad.killer_error"))
            return
        }

        
        if (plugin.spectatorManager.isSpectator(player)) return

        val loc = block.location

        if (plugin.generatorManager.isCompleted(loc)) {
            player.sendMessage(MessageService.getComponent(player, "listeners.keypad.already_solved"))
            return
        }

        event.isCancelled = true
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)

        if (activeTyping.containsKey(player.uniqueId)) {
            player.sendMessage(MessageService.getComponent(
                player, "listeners.keypad.already_typing",
                Placeholder.parsed("cancel", cancelWord(player))
            ))
            return
        }

        
        val digits = (0..9).shuffled().take(4)
        val isAscending = Random().nextBoolean()
        
        val sortedDigits = if (isAscending) digits.sorted() else digits.sortedDescending()
        val answer = sortedDigits.joinToString("")
        
        val modePath = if (isAscending) "listeners.keypad.mode_ascending" else "listeners.keypad.mode_descending"
        val modeText = MessageService.getRawString(player, modePath, "?", "messages")
        val puzzleText = digits.joinToString(", ")

        activeTyping[player.uniqueId] = Pair(loc, answer)

        val messages = MessageService
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f)
        player.sendMessage(messages.getComponent(player, "listeners.keypad.header"))
        player.sendMessage(messages.getComponent(player, "listeners.keypad.instructions", Placeholder.parsed("mode", modeText)))
        player.sendMessage(messages.getComponent(player, "listeners.keypad.digits", Placeholder.parsed("digits", puzzleText)))
        player.sendMessage(messages.getComponent(player, "listeners.keypad.input_hint", Placeholder.parsed("cancel", cancelWord(player))))
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val typingData = activeTyping[player.uniqueId] ?: return

        event.isCancelled = true

        val loc = typingData.first
        val answer = typingData.second
        val input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()

        
        
        val cancels = setOf(cancelWord(player).lowercase(), "cancelar", "cancel")
        if (input.lowercase() in cancels) {
            activeTyping.remove(player.uniqueId)
            player.sendMessage(MessageService.getComponent(player, "listeners.keypad.cancelled"))
            return
        }

        if (input == answer) {
            
            activeTyping.remove(player.uniqueId)
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                plugin.generatorManager.addProgress(loc, 100) 
                player.sendMessage(MessageService.getComponent(player, "listeners.keypad.success"))
            })
        } else {
            
            activeTyping.remove(player.uniqueId)
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                player.sendMessage(MessageService.getComponent(player, "listeners.keypad.fail"))
            })
        }
    }
}
