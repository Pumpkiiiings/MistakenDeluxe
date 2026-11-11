package liric.mistaken.listeners.interactables

import liric.mistaken.Mistaken
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * Shared base listener for in-game interactable objective blocks
 * ([GeneratorListener], [HackTerminalListener], [KeypadListener]).
 */
abstract class BaseInteractableListener(
    protected val plugin: Mistaken,
    private val targetMaterial: Material
) : Listener {

    protected abstract fun killerErrorMessage(player: Player): Component
    protected abstract fun alreadyCompletedMessage(player: Player): Component
    protected abstract fun onInteractableUse(player: Player, loc: Location)

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.type != targetMaterial) return

        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return

        if (plugin.spectatorManager.isSpectator(player)) return

        if (session.isKiller(player.uniqueId)) {
            player.sendMessage(killerErrorMessage(player))
            return
        }

        val loc = block.location
        if (plugin.generatorManager.isCompleted(loc)) {
            player.sendMessage(alreadyCompletedMessage(player))
            return
        }

        event.isCancelled = true
        event.setUseItemInHand(Event.Result.DENY)
        event.setUseInteractedBlock(Event.Result.DENY)

        onInteractableUse(player, loc)
    }
}
