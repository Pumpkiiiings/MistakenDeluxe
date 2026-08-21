package liric.mistaken.listeners

import liric.mistaken.Mistaken
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import pumpking.lib.service.PumpkingServiceManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class KeypadListener(private val plugin: Mistaken) : Listener {

    // Unique ID -> Pair<Location, AnswerCode>
    private val activeTyping = ConcurrentHashMap<UUID, Pair<Location, String>>()

    private fun cancelWord(player: Player): String =
        PumpkingServiceManager.messages.getRawString(player, "listeners.keypad.cancel_word", "cancelar", "messages")

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.type != Material.AMETHYST_BLOCK) return

        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return

        if (session.isKiller(player.uniqueId)) {
            player.sendMessage(PumpkingServiceManager.messages.getComponent(player, "listeners.keypad.killer_error"))
            return
        }

        // ?? FIX: Espectadores no pueden usar el keypad
        if (plugin.spectatorManager.isSpectator(player)) return

        val loc = block.location

        if (plugin.generatorManager.isCompleted(loc)) {
            player.sendMessage(PumpkingServiceManager.messages.getComponent(player, "listeners.keypad.already_solved"))
            return
        }

        event.isCancelled = true

        if (activeTyping.containsKey(player.uniqueId)) {
            player.sendMessage(PumpkingServiceManager.messages.getComponent(
                player, "listeners.keypad.already_typing",
                Placeholder.parsed("cancel", cancelWord(player))
            ))
            return
        }

        // Generate a quick logic puzzle (Sort 4 digits)
        val digits = (0..9).shuffled().take(4)
        val isAscending = Random().nextBoolean()
        
        val sortedDigits = if (isAscending) digits.sorted() else digits.sortedDescending()
        val answer = sortedDigits.joinToString("")
        
        val modePath = if (isAscending) "listeners.keypad.mode_ascending" else "listeners.keypad.mode_descending"
        val modeText = PumpkingServiceManager.messages.getRawString(player, modePath, "?", "messages")
        val puzzleText = digits.joinToString(", ")

        activeTyping[player.uniqueId] = Pair(loc, answer)

        val messages = PumpkingServiceManager.messages
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f)
        player.sendMessage(messages.getComponent(player, "listeners.keypad.header"))
        player.sendMessage(messages.getComponent(player, "listeners.keypad.instructions", Placeholder.parsed("mode", modeText)))
        player.sendMessage(messages.getComponent(player, "listeners.keypad.digits", Placeholder.parsed("digits", puzzleText)))
        player.sendMessage(messages.getComponent(player, "listeners.keypad.input_hint", Placeholder.parsed("cancel", cancelWord(player))))
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val typingData = activeTyping[player.uniqueId] ?: return

        event.isCancelled = true

        val loc = typingData.first
        val answer = typingData.second
        val input = event.message.trim()

        // Se acepta la palabra del idioma del player y ademas los dos literales base,
        // para que nadie se quede atrapado en el panel si cambia de idioma a mitad.
        val cancels = setOf(cancelWord(player).lowercase(), "cancelar", "cancel")
        if (input.lowercase() in cancels) {
            activeTyping.remove(player.uniqueId)
            player.sendMessage(PumpkingServiceManager.messages.getComponent(player, "listeners.keypad.cancelled"))
            return
        }

        if (input == answer) {
            // Success
            activeTyping.remove(player.uniqueId)
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                plugin.generatorManager.addProgress(loc, 100) // Instantly complete this objective
                player.sendMessage(PumpkingServiceManager.messages.getComponent(player, "listeners.keypad.success"))
            })
        } else {
            // Fail
            activeTyping.remove(player.uniqueId)
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                player.sendMessage(PumpkingServiceManager.messages.getComponent(player, "listeners.keypad.fail"))
            })
        }
    }
}
