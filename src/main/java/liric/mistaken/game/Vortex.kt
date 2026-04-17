package liric.mistaken.game

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.play.server.*
import liric.mistaken.Mistaken
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer

/**
 * [LIRIC-MISTAKEN 2.0]
 * TerrorPacketFactory: Inyección de terror vía paquetes.
 * FIX: Reemplazo de Corrutinas por EntityScheduler (Paper) para evitar tareas zombie y crashes.
 */
class Vortex(private val plugin: Mistaken) {

    /**
     * Invoca una sombra que solo ve la víctima.
     */
    fun spawnShadowEntity(victim: Player, loc: Location, ticks: Int) {
        if (!victim.isOnline) return

        val fakeId = ThreadLocalRandom.current().nextInt(1_000_000, 2_000_000)
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
        val affected = ArrayList<Location>()
        val pm = PacketEvents.getAPI().playerManager

        // Generar ráfaga de paquetes (Cálculo matemático ligero)
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                // Solo afectamos el suelo debajo de ellos (-1)
                val target = center.clone().add(x.toDouble(), -1.0, z.toDouble())
                affected.add(target)

                val pos = Vector3i(target.blockX, target.blockY, target.blockZ)
                pm.sendPacket(victim, WrapperPlayServerBlockChange(pos, 0))
            }
        }

        // Restauración optimizada (15 ticks = 750ms)
        victim.scheduler.runDelayed(plugin, Consumer { _ ->
            // Restauramos en masa usando Bukkit
            affected.forEach { loc ->
                if (victim.isOnline) {
                    victim.sendBlockChange(loc, loc.block.blockData)
                }
            }
        }, null, 15L)
    }
}
