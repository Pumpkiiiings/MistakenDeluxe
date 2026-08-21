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
import java.util.concurrent.ConcurrentHashMap
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.function.Consumer
import liric.mistaken.packet.PacketFactory
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

abstract class VirtualDisplay(
    var location: Location,
    initialViewers: Collection<Player>,
    val entityType: EntityType
) {
    val entityId: Int = PacketFactory.generateEntityId()
    val uuid: UUID = UUID.randomUUID()
    val uniqueId: UUID get() = uuid
    var isValid: Boolean = true

    
    
    private val viewerIds: MutableSet<UUID> =
        ConcurrentHashMap.newKeySet<UUID>().apply { initialViewers.forEach { add(it.uniqueId) } }

    /** Espectadores resueltos en el momento de la consulta. Nunca cachear el resultado. */
    val viewers: List<Player>
        get() = viewerIds.mapNotNull { Bukkit.getPlayer(it) }

    /** Añade un espectador. Si el display ya está vivo, se lo spawnea solo a él. */
    fun addViewer(player: Player) {
        if (!viewerIds.add(player.uniqueId)) return
        if (isValid) sendTo(player, buildSpawnPacket(), *buildMetadataPacket())
    }

    /** Quita un espectador y le destruye la entidad en su cliente. */
    fun removeViewer(player: Player) {
        if (!viewerIds.remove(player.uniqueId)) return
        if (isValid) sendTo(player, WrapperPlayServerDestroyEntities(entityId))
    }

    /**
     * Reemplaza la lista de espectadores aplicando el diff: spawnea para los nuevos,
     * destruye para los que salen. Para displays de vida larga (hologramas de generador,
     * cinemáticas) llamado cuando alguien entra o sale de la sesión.
     */
    fun setViewers(newViewers: Collection<Player>) {
        val incoming = newViewers.map { it.uniqueId }.toSet()

        viewerIds.filterNot { it in incoming }.forEach { id ->
            viewerIds.remove(id)
            if (isValid) Bukkit.getPlayer(id)?.let { sendTo(it, WrapperPlayServerDestroyEntities(entityId)) }
        }
        newViewers.forEach { addViewer(it) }
    }

    var isPersistent: Boolean = false
    fun setGravity(gravity: Boolean) {}

    val world: World
        get() = location.world

    private fun buildSpawnPacket(): PacketWrapper<*> = WrapperPlayServerSpawnEntity(
        entityId,
        Optional.of(uuid),
        entityType,
        Vector3d(location.x, location.y, location.z),
        location.pitch,
        location.yaw,
        0f,
        0,
        Optional.empty()
    )

    private fun buildMetadataPacket(): Array<PacketWrapper<*>> {
        val metadata = buildMetadata()
        return if (metadata.isEmpty()) emptyArray()
        else arrayOf(WrapperPlayServerEntityMetadata(entityId, metadata))
    }

    fun spawn() {
        if (!isValid) return
        sendPacket(buildSpawnPacket())
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

    private fun sendTo(player: Player, vararg packets: PacketWrapper<*>) {
        if (!player.isOnline) return
        val pm = PacketEvents.getAPI().playerManager
        packets.forEach { pm.sendPacket(player, it) }
    }

    protected fun sendPacket(packet: Any) {
        val wrapper = packet as PacketWrapper<*>
        val pm = PacketEvents.getAPI().playerManager
        
        
        viewerIds.forEach { id ->
            val player = Bukkit.getPlayer(id) ?: return@forEach
            if (player.isOnline) pm.sendPacket(player, wrapper)
        }
    }

    fun updateMetadata() {
        if (!isValid) return
        buildMetadataPacket().forEach { sendPacket(it) }
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
