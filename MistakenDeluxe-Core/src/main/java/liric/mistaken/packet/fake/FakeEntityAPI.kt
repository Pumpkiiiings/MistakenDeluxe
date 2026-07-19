package liric.mistaken.packet.fake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.type.EntityType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

class FakeEntityAPI {

    /**
     * Spawnea una entidad falsa (Client-Side).
     * Útil para crear hitboxes invisibles o efectos decorativos.
     * @param player Jugador objetivo
     * @param location Ubicación
     * @param type Tipo de Entidad (PacketEvents EntityType)
     * @return El ID de Entidad generado.
     */
    fun spawnEntity(player: Player, location: Location, type: EntityType): Int {
        val entityId = liric.mistaken.packet.PacketFactory.generateEntityId()
        val uuid = UUID.randomUUID()
        val pos = SpigotConversionUtil.fromBukkitLocation(location)

        val spawnPacket = WrapperPlayServerSpawnEntity(
            entityId,
            uuid,
            type,
            pos.position,
            pos.pitch,
            pos.yaw,
            pos.yaw,
            0,
            java.util.Optional.empty()
        )

        PacketEvents.getAPI().playerManager.sendPacket(player, spawnPacket)
        return entityId
    }

    /**
     * Destruye cualquier entidad falsa generada previamente.
     */
    fun destroyEntity(player: Player, entityId: Int) {
        val destroy = WrapperPlayServerDestroyEntities(entityId)
        PacketEvents.getAPI().playerManager.sendPacket(player, destroy)
    }
}
