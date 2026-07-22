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
import pumpking.lib.color.ColorTranslator
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class KeypadListener(private val plugin: Mistaken) : Listener {

    // Unique ID -> Pair<Location, AnswerCode>
    private val activeTyping = ConcurrentHashMap<UUID, Pair<Location, String>>()

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.type != Material.AMETHYST_BLOCK) return

        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return

        if (session.isKiller(player.uniqueId)) {
            player.sendMessage(ColorTranslator.translate("<red>¡Los asesinos no pueden usar los paneles de código!"))
            return
        }

        val loc = block.location

        if (plugin.generatorManager.isCompleted(loc)) {
            player.sendMessage(ColorTranslator.translate("<red>Panel de código ya resuelto."))
            return
        }

        event.isCancelled = true

        if (activeTyping.containsKey(player.uniqueId)) {
            player.sendMessage(ColorTranslator.translate("<yellow>Ya estás intentando hackear un panel. Escribe el código en el chat o 'cancelar'."))
            return
        }

        // Generate a quick logic puzzle (Sort 4 digits)
        val digits = (0..9).shuffled().take(4)
        val isAscending = Random().nextBoolean()
        
        val sortedDigits = if (isAscending) digits.sorted() else digits.sortedDescending()
        val answer = sortedDigits.joinToString("")
        
        val modeText = if (isAscending) "MENOR a MAYOR" else "MAYOR a MENOR"
        val puzzleText = digits.joinToString(", ")

        activeTyping[player.uniqueId] = Pair(loc, answer)

        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f)
        player.sendMessage(ColorTranslator.translate("<dark_aqua>--- PANEL DE SEGURIDAD ---"))
        player.sendMessage(ColorTranslator.translate("<white>Para desbloquear el progreso, ordena estos números de <yellow><bold>$modeText<white>:"))
        player.sendMessage(ColorTranslator.translate("<gray>[ $puzzleText ]"))
        player.sendMessage(ColorTranslator.translate("<white>Escribe el código de 4 dígitos en el chat (Ej: 1234) o escribe 'cancelar'."))
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val typingData = activeTyping[player.uniqueId] ?: return

        event.isCancelled = true

        val loc = typingData.first
        val answer = typingData.second
        val input = event.message.trim()

        if (input.equals("cancelar", ignoreCase = true)) {
            activeTyping.remove(player.uniqueId)
            player.sendMessage(ColorTranslator.translate("<red>Has cancelado el hackeo del panel."))
            return
        }

        if (input == answer) {
            // Success
            activeTyping.remove(player.uniqueId)
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                plugin.generatorManager.addProgress(loc, 100) // Instantly complete this objective
                player.sendMessage(ColorTranslator.translate("<green>¡Código correcto! Panel desbloqueado. (+100%)"))
            })
        } else {
            // Fail
            activeTyping.remove(player.uniqueId)
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                player.sendMessage(ColorTranslator.translate("<red>Código incorrecto. El panel se ha bloqueado temporalmente."))
            })
        }
    }
}
