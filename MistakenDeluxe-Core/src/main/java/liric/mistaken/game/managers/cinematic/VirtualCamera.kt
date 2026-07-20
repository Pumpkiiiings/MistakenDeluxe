package liric.mistaken.game.managers.cinematic

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.*
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.Optional
import java.util.UUID

class VirtualCamera(private val player: Player) {

    private var entityId: Int = -1
    private var isSpectating = false

    fun startSpectating(initialPoint: Location) {
        if (isSpectating) return
        
        entityId = 2000000 + (Math.random() * 100000).toInt()

        // 1. Spawn invisible ArmorStand
        val spawnPacket = WrapperPlayServerSpawnEntity(
            entityId,
            Optional.of(UUID.randomUUID()),
            EntityTypes.ARMOR_STAND,
            Vector3d(initialPoint.x, initialPoint.y, initialPoint.z),
            initialPoint.pitch,
            initialPoint.yaw,
            0f, 0, Optional.empty()
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, spawnPacket)

        // 2. Set metadata (Invisible, index 0, bitmask 0x20)
        val metadataPacket = WrapperPlayServerEntityMetadata(
            entityId,
            listOf(EntityData(0, EntityDataTypes.BYTE, 0x20.toByte()))
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, metadataPacket)

        // 3. Force player camera to spectate the ArmorStand
        val cameraPacket = WrapperPlayServerCamera(entityId)
        PacketEvents.getAPI().playerManager.sendPacket(player, cameraPacket)
        
        isSpectating = true
    }

    fun updatePosition(loc: Location) {
        if (!isSpectating) return

        // Teleport the fake entity
        val tpPacket = WrapperPlayServerEntityTeleport(
            entityId,
            Vector3d(loc.x, loc.y, loc.z),
            loc.yaw,
            loc.pitch,
            true
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, tpPacket)

        // Update head look
        val headLook = WrapperPlayServerEntityHeadLook(
            entityId,
            loc.yaw
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, headLook)
    }

    fun stopSpectating() {
        if (!isSpectating) return

        // 1. Restore camera to player
        val cameraPacket = WrapperPlayServerCamera(player.entityId)
        PacketEvents.getAPI().playerManager.sendPacket(player, cameraPacket)

        // 2. Destroy the fake entity
        val destroyPacket = WrapperPlayServerDestroyEntities(entityId)
        PacketEvents.getAPI().playerManager.sendPacket(player, destroyPacket)

        isSpectating = false
    }
}
