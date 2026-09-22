package liric.mistaken.preview.engine

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.Optional
import java.util.UUID

object EntityFactory {

    private var nextEntityId = 50000

    fun spawnTextDisplay(player: Player, location: Location, text: String): Int {
        val entityId = nextEntityId++
        val uuid = UUID.randomUUID()
        
        val spawnPacket = WrapperPlayServerSpawnEntity(
            entityId,
            Optional.of(uuid),
            EntityTypes.TEXT_DISPLAY,
            Vector3d(location.x, location.y, location.z),
            location.pitch,
            location.yaw,
            location.yaw,
            0,
            Optional.empty()
        )
        
        val comp = MiniMessage.miniMessage().deserialize(text)

        val metadataPacket = WrapperPlayServerEntityMetadata(
            entityId,
            listOf(
                // 22 es la posición de 'text' en TextDisplay en 1.20+ (puede variar por versión)
                // Usamos PacketEvents tipos predefinidos.
                // 23: TextComponent (NO es Optional en TextDisplay)
                EntityData(23, EntityDataTypes.ADV_COMPONENT, comp),
                // 15: billboard (byte, 3 = CENTER)
                EntityData(15, EntityDataTypes.BYTE, 3.toByte()),
                // 25: background color (int, 0 = transparent)
                EntityData(25, EntityDataTypes.INT, 0x00000000)
            )
        )
        
        PacketEvents.getAPI().playerManager.sendPacket(player, spawnPacket)
        PacketEvents.getAPI().playerManager.sendPacket(player, metadataPacket)
        
        return entityId
    }

    fun destroyEntity(player: Player, entityId: Int) {
        if (entityId == -1) return
        val destroyPacket = WrapperPlayServerDestroyEntities(entityId)
        PacketEvents.getAPI().playerManager.sendPacket(player, destroyPacket)
    }
}
