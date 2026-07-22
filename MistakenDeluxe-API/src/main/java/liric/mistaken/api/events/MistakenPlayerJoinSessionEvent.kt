package liric.mistaken.api.events

import liric.mistaken.api.managers.ISession
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Evento disparado cuando un jugador se une a una partida de Mistaken.
 */
class MistakenPlayerJoinSessionEvent(
    val player: Player,
    val session: ISession
) : Event() {

    override fun getHandlers(): HandlerList {
        return handlerList
    }

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
