package liric.mistaken.packet.fake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

class FakeGlowAPI {

    /**
     * Aplica o remueve un efecto de Glowing Client-Side a una entidad existente.
     * Esto NO requiere que la entidad real esté brillando en Bukkit.
     * @param viewer El player que verá el brillo.
     * @param target La entidad que brillará.
     * @param isGlowing true para activar, false para clear el estado forzado.
     */
    fun setGlowing(viewer: Player, target: Entity, isGlowing: Boolean) {
        
        
        
        
        
        
        val flagValue: Byte = if (isGlowing) 0x40 else 0x00
        
        val metadata = WrapperPlayServerEntityMetadata(
            target.entityId,
            listOf(EntityData(0, EntityDataTypes.BYTE, flagValue))
        )
        
        PacketEvents.getAPI().playerManager.sendPacket(viewer, metadata)
    }
}
