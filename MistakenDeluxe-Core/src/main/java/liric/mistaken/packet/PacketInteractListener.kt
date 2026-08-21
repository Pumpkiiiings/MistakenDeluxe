package liric.mistaken.packet

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.Mistaken

/**
 * Escucha interacciones del cliente con Entidades Falsas.
 */
class PacketInteractListener : PacketListenerAbstract() {

    
    companion object {
        private val clickCallbacks = ConcurrentHashMap<Int, (Player, WrapperPlayClientInteractEntity.InteractAction) -> Unit>()

        /**
         * Registra una acción a ejecutarse cuando un player interactúa con una entidad falsa.
         */
        fun registerCallback(entityId: Int, callback: (Player, WrapperPlayClientInteractEntity.InteractAction) -> Unit) {
            clickCallbacks[entityId] = callback
        }

        fun removeCallback(entityId: Int) {
            clickCallbacks.remove(entityId)
        }
    }

    override fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.packetType == PacketType.Play.Client.INTERACT_ENTITY) {
            val interact = WrapperPlayClientInteractEntity(event)
            val entityId = interact.entityId
            
            val callback = clickCallbacks[entityId]
            if (callback != null) {
                val player = (event.getPlayer() as? Player) ?: return
                
                
                
                
                Bukkit.getScheduler().runTask(Mistaken.instance, Runnable {
                    callback.invoke(player, interact.action)
                })
            }
        }
    }
}
