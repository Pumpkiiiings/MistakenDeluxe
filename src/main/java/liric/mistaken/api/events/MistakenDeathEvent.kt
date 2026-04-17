package liric.mistaken.api.events

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * [LIRIC-MISTAKEN 2.0]
 * MistakenDeathEvent: Evento disparado cuando un asesino elimina a un superviviente
 * o viceversa dentro de la lógica del juego.
 */
class MistakenDeathEvent(
    val victim: Player,
    val killer: Player
) : Event() {

    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmField
        val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
