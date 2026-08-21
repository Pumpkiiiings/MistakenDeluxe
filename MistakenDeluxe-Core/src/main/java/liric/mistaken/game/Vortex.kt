package liric.mistaken.game

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.play.server.*
import liric.mistaken.Mistaken
import liric.mistaken.packet.PacketFactory
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.*
import java.util.function.Consumer


class Vortex(private val plugin: Mistaken) {

    /**
     * Invoca una sombra que solo ve la víctima.
     */
    fun spawnShadowEntity(victim: Player, loc: Location, ticks: Int) {
        if (!victim.isOnline) return

        
        
        
        
        val fakeId = PacketFactory.generateEntityId()
        val uuid = UUID.randomUUID()

        
        val spawnPacket = WrapperPlayServerSpawnEntity(
            fakeId,
            Optional.of(uuid),
            EntityTypes.SKELETON,
            Vector3d(loc.x, loc.y, loc.z),
            loc.pitch,
            loc.yaw,
            loc.yaw,
            0,
            Optional.of(Vector3d(0.0, 0.0, 0.0))
        )

        
        val metadataPacket = WrapperPlayServerEntityMetadata(
            fakeId,
            listOf(EntityData(0, EntityDataTypes.BYTE, 0x60.toByte()))
        )

        
        val pm = PacketEvents.getAPI().playerManager
        pm.sendPacket(victim, spawnPacket)
        pm.sendPacket(victim, metadataPacket)

        
        
        victim.scheduler.runDelayed(plugin, Consumer { _ ->
            pm.sendPacket(victim, WrapperPlayServerDestroyEntities(fakeId))
        }, null, ticks.toLong())
    }

    /**
     * Glitch visual: El suelo desaparece temporalmente.
     */
    fun sendFakeAir(victim: Player, loc: Location, ticks: Int) {
        if (!victim.isOnline) return

        val pm = PacketEvents.getAPI().playerManager
        val pos = Vector3i(loc.blockX, loc.blockY, loc.blockZ)

        
        pm.sendPacket(victim, WrapperPlayServerBlockChange(pos, 0))

        
        victim.scheduler.runDelayed(plugin, Consumer { _ ->
            if (victim.isOnline) {
                
                victim.sendBlockChange(loc, loc.block.blockData)
            }
        }, null, ticks.toLong())
    }

    /**
     * Ataque de pánico: Efecto de daño visual y sonido.
     */
    fun sendFakeHit(victim: Player) {
        if (!victim.isOnline) return

        
        PacketEvents.getAPI().playerManager.sendPacket(
            victim,
            WrapperPlayServerEntityStatus(victim.entityId, 2)
        )

        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 0.5f)
    }

    /**
     * Corrupción de Terreno masiva (Ráfaga de paquetes).
     */
    fun sendRealityCollapse(victim: Player, radius: Int) {
        if (!victim.isOnline) return

        val center = victim.location
        val world = center.world
        val y = center.blockY - 1 

        
        
        
        val bySection = HashMap<Vector3i, MutableList<WrapperPlayServerMultiBlockChange.EncodedBlock>>()
        val affected = ArrayList<Location>((2 * radius + 1) * (2 * radius + 1))

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val bx = center.blockX + dx
                val bz = center.blockZ + dz
                affected.add(Location(world, bx.toDouble(), y.toDouble(), bz.toDouble()))

                val section = Vector3i(bx shr 4, y shr 4, bz shr 4)
                
                bySection.getOrPut(section) { mutableListOf() }
                    .add(WrapperPlayServerMultiBlockChange.EncodedBlock(0, bx and 15, y and 15, bz and 15))
            }
        }

        val pm = PacketEvents.getAPI().playerManager
        bySection.forEach { (section, blocks) ->
            pm.sendPacket(victim, WrapperPlayServerMultiBlockChange(section, true, blocks.toTypedArray()))
        }

        
        
        
        plugin.server.regionScheduler.runDelayed(plugin, center, Consumer { _ ->
            if (!victim.isOnline) return@Consumer
            affected.forEach { loc -> victim.sendBlockChange(loc, loc.block.blockData) }
        }, 15L)
    }
}
