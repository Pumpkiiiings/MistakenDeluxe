package liric.mistaken.api.level.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired when a player's level increases.
 */
class PlayerLevelUpEvent(
    val player: Player,
    val oldLevel: Int,
    val newLevel: Int
) : Event() {

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }
}
