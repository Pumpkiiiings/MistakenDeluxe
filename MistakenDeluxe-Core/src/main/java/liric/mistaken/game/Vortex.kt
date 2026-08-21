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

        // Los IDs de entidad reales de Bukkit son secuenciales desde 0 y en un servidor
        // con uptime largo alcanzan el rango de millones. Un ID aleatorio ahí colisiona
        // con una entidad real y el DestroyEntities de más abajo la borra en el cliente.
        // PacketFactory reserva un rango alto para las falsas.
        val fakeId = PacketFactory.generateEntityId()
        val uuid = UUID.randomUUID()

        // 1. Construcción del Paquete (Skeleton para la forma)
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

        // 2. Metadata: Invisible (0x20) + Glowing (0x40) = 0x60
        val metadataPacket = WrapperPlayServerEntityMetadata(
            fakeId,
            listOf(EntityData(0, EntityDataTypes.BYTE, 0x60.toByte()))
        )

        // 3. Envío seguro
        val pm = PacketEvents.getAPI().playerManager
        pm.sendPacket(victim, spawnPacket)
        pm.sendPacket(victim, metadataPacket)

        // 4. 🔥 FIX: Destrucción de entidad usando Consumer para el EntityScheduler.
        // Además, en PacketEvents moderno WrapperPlayServerDestroyEntities toma un `int...` (vararg de ints).
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

        // Enviamos aire (GlobalState 0 suele representar el aire en la mayoría de mappers de PE)
        pm.sendPacket(victim, WrapperPlayServerBlockChange(pos, 0))

        // Restauración automática
        victim.scheduler.runDelayed(plugin, Consumer { _ ->
            if (victim.isOnline) {
                // Al estar en el EntityScheduler, es seguro hacer sendBlockChange (API de Bukkit)
                victim.sendBlockChange(loc, loc.block.blockData)
            }
        }, null, ticks.toLong())
    }

    /**
     * Ataque de pánico: Efecto de daño visual y sonido.
     */
    fun sendFakeHit(victim: Player) {
        if (!victim.isOnline) return

        // Status 2 = Hurt Animation
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
        val y = center.blockY - 1 // Solo el suelo bajo sus pies

        // Agrupamos por sección de chunk (16x16x16). Un BlockChange por bloque son
        // (2r+1)^2 paquetes en un tick — con radio 10 eso es 441 a un solo player.
        // MultiBlockChange manda una sección entera en un paquete: ~6 en total.
        val bySection = HashMap<Vector3i, MutableList<WrapperPlayServerMultiBlockChange.EncodedBlock>>()
        val affected = ArrayList<Location>((2 * radius + 1) * (2 * radius + 1))

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val bx = center.blockX + dx
                val bz = center.blockZ + dz
                affected.add(Location(world, bx.toDouble(), y.toDouble(), bz.toDouble()))

                val section = Vector3i(bx shr 4, y shr 4, bz shr 4)
                // Coordenadas relativas a la sección (0-15)
                bySection.getOrPut(section) { mutableListOf() }
                    .add(WrapperPlayServerMultiBlockChange.EncodedBlock(0, bx and 15, y and 15, bz and 15))
            }
        }

        val pm = PacketEvents.getAPI().playerManager
        bySection.forEach { (section, blocks) ->
            pm.sendPacket(victim, WrapperPlayServerMultiBlockChange(section, true, blocks.toTypedArray()))
        }

        // Restauración: leemos el estado real en el scheduler de la REGIÓN de cada bloque.
        // victim.scheduler pertenece a la región del player; tocar loc.block de una
        // región distinta revienta en Folia.
        plugin.server.regionScheduler.runDelayed(plugin, center, Consumer { _ ->
            if (!victim.isOnline) return@Consumer
            affected.forEach { loc -> victim.sendBlockChange(loc, loc.block.blockData) }
        }, 15L)
    }
}
