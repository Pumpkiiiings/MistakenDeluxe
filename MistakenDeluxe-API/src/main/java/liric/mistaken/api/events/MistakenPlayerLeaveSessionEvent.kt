package liric.mistaken.api.events

import liric.mistaken.api.managers.ISession
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Evento disparado cuando un jugador sale o es expulsado de una partida de Mistaken.
 */
class MistakenPlayerLeaveSessionEvent(
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
