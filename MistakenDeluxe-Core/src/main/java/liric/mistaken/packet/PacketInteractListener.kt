package liric.mistaken.packet

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * Escucha interacciones del cliente con Entidades Falsas.
 */
class PacketInteractListener : PacketListenerAbstract() {

    // Almacena callbacks para entidades falsas: EntityID -> Acción(Player)
    companion object {
        private val clickCallbacks = ConcurrentHashMap<Int, (Player, WrapperPlayClientInteractEntity.InteractAction) -> Unit>()

        /**
         * Registra una acción a ejecutarse cuando un jugador interactúa con una entidad falsa.
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
                val player = event.player as? Player ?: return
                
                // Las acciones suelen ser INTERACT o ATTACK
                // Ejecutamos en el Main Thread porque Bukkit API (ej. abrir menú) 
                // fallará si se ejecuta en el hilo asíncrono de Netty.
                Bukkit.getScheduler().runTask(liric.mistaken.Mistaken.instance, Runnable {
                    callback.invoke(player, interact.action)
                })
            }
        }
    }
}
