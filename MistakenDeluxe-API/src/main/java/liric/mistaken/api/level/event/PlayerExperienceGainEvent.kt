package liric.mistaken.api.level.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired when a player gains experience.
 * Can be cancelled or the amount can be modified (e.g. for boosters).
 */
class PlayerExperienceGainEvent(
    val player: Player,
    var amount: Long,
    val reason: GainReason
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean {
        return cancelled
    }

    override fun setCancelled(cancel: Boolean) {
        this.cancelled = cancel
    }

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

    enum class GainReason {
        KILL,
        QUEST,
        COMMAND,
        PLUGIN_API,
        UNKNOWN
    }
}
