package liric.mistaken.packet.fake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityType
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.Optional
import java.util.UUID
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.function.Consumer
import liric.mistaken.packet.PacketFactory
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

abstract class VirtualDisplay(
    var location: Location,
    val viewers: List<Player>,
    val entityType: EntityType
) {
    val entityId: Int = PacketFactory.generateEntityId()
    val uuid: UUID = UUID.randomUUID()
    val uniqueId: UUID get() = uuid
    var isValid: Boolean = true

    var isPersistent: Boolean = false
    fun setGravity(gravity: Boolean) {}

    val world: World
        get() = location.world

    fun spawn() {
        if (!isValid) return
        val locPE = Vector3d(location.x, location.y, location.z)
        val spawnPacket = WrapperPlayServerSpawnEntity(
            entityId,
            Optional.of(uuid),
            entityType,
            locPE,
            location.pitch,
            location.yaw,
            0f,
            0,
            Optional.empty()
        )
        sendPacket(spawnPacket)
        updateMetadata()
    }

    fun teleport(newLoc: Location) {
        if (!isValid) return
        this.location = newLoc
        val locPE = Vector3d(newLoc.x, newLoc.y, newLoc.z)
        val tpPacket = WrapperPlayServerEntityTeleport(
            entityId,
            locPE,
            newLoc.yaw,
            newLoc.pitch,
            true
        )
        sendPacket(tpPacket)
    }

    fun remove() {
        if (!isValid) return
        isValid = false
        val destroyPacket = WrapperPlayServerDestroyEntities(entityId)
        sendPacket(destroyPacket)
    }

    protected fun sendPacket(packet: Any) {
        viewers.forEach { player ->
            if (player.isOnline) {
                PacketEvents.getAPI().playerManager.sendPacket(player, packet as PacketWrapper<*>)
            }
        }
    }

    fun updateMetadata() {
        if (!isValid) return
        val metadata = buildMetadata()
        if (metadata.isNotEmpty()) {
            val metaPacket = WrapperPlayServerEntityMetadata(entityId, metadata)
            sendPacket(metaPacket)
        }
    }

    abstract fun buildMetadata(): List<EntityData<*>>

    fun getNearbyEntities(x: Double, y: Double, z: Double): Collection<Entity> {
        return location.world.getNearbyEntities(location, x, y, z)
    }

    val scheduler: VirtualScheduler = VirtualScheduler()

    inner class VirtualScheduler {
        fun runAtFixedRate(
            plugin: Plugin,
            task: Consumer<ScheduledTask>,
            retired: Runnable?,
            initialDelayTicks: Long,
            periodTicks: Long
        ): ScheduledTask {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task, initialDelayTicks, periodTicks)
        }

        fun runDelayed(
            plugin: Plugin,
            task: Consumer<ScheduledTask>,
            retired: Runnable?,
            delayTicks: Long
        ): ScheduledTask {
            return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task, delayTicks)
        }
    }
}
