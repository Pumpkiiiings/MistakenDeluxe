package liric.mistaken.api.events

import liric.mistaken.api.managers.ISession
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MistakenGameStartEvent(val session: ISession) : Event(), Cancellable {
    private var isCancelled = false

    override fun isCancelled(): Boolean = isCancelled
    override fun setCancelled(cancel: Boolean) {
        isCancelled = cancel
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}

class MistakenGameEndEvent(val session: ISession, val killerWon: Boolean) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}

class MistakenGeneratorFixEvent(val player: Player, val location: Location) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}

class MistakenPlayerEscapeEvent(val player: Player, val session: ISession) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}

class MistakenKillerAttackEvent(
    val killer: Player,
    val survivor: Player,
    val session: ISession,
    var damage: Double
) : Event(), Cancellable {
    private var isCancelled = false

    override fun isCancelled(): Boolean = isCancelled
    override fun setCancelled(cancel: Boolean) {
        isCancelled = cancel
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
