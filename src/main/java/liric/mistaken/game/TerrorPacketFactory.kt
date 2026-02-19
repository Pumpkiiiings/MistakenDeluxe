package liric.mistaken.game

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.play.server.*
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.utils.mainThread // IMPORTANTE: Nuestra extensión
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0]
 * TerrorPacketFactory: Inyección de terror vía paquetes (Thread-Safe).
 */
class TerrorPacketFactory(private val plugin: Mistaken) {

    // Scope para manejar las tareas de paquetes sin bloquear el servidor
    private val factoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Invoca una sombra que solo ve la víctima.
     * Optimizado: Uso de Coroutines para la limpieza.
     */
    fun spawnShadowEntity(victim: Player, loc: Location, ticks: Int) {
        if (!victim.isOnline) return

        val fakeId = ThreadLocalRandom.current().nextInt(1_000_000, 2_000_000)
        val uuid = UUID.randomUUID()

        // Paquete de Spawn (SKELETON para silueta humana/aterradora)
        val spawn = WrapperPlayServerSpawnEntity(
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

        // Metadata: Invisible (0x20) + Glowing (0x40) = 0x60
        val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x60.toByte()))
        val metaPacket = WrapperPlayServerEntityMetadata(fakeId, metadata)

        val playerManager = PacketEvents.getAPI().playerManager
        playerManager.sendPacket(victim, spawn)
        playerManager.sendPacket(victim, metaPacket)

        // Tarea de limpieza asíncrona
        factoryScope.launch {
            delay(ticks * 50L) // Convertir ticks a ms
            if (victim.isOnline) {
                playerManager.sendPacket(victim, WrapperPlayServerDestroyEntities(fakeId))
            }
        }
    }

    /**
     * Glitch visual: El suelo desaparece temporalmente para el jugador.
     */
    fun sendFakeAir(victim: Player, loc: Location, ticks: Int) {
        if (!victim.isOnline) return

        val playerManager = PacketEvents.getAPI().playerManager
        val pos = Vector3i(loc.blockX, loc.blockY, loc.blockZ)

        // 0 es AIRE en la mayoría de versiones, pero PacketEvents maneja BlockState ID
        playerManager.sendPacket(victim, WrapperPlayServerBlockChange(pos, 0))

        factoryScope.launch {
            delay(ticks * 50L)
            if (victim.isOnline) {
                // --- ARREGLO: Reemplazado Dispatchers.Main por plugin.mainThread ---
                withContext(plugin.mainThread) {
                    victim.sendBlockChange(loc, loc.block.blockData)
                }
            }
        }
    }

    /**
     * Ataque de pánico: Efecto de daño visual y sonido de dolor.
     */
    fun sendFakeHit(victim: Player) {
        if (!victim.isOnline) return

        // Status 2 = Entity Hurt Animation
        PacketEvents.getAPI().playerManager.sendPacket(
            victim,
            WrapperPlayServerEntityStatus(victim.entityId, 2)
        )

        // El sonido se reproduce en el hilo principal para sincronización con el cliente
        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 0.5f)
    }

    /**
     * Corrupción de Terreno masiva.
     * Optimización: Envía paquetes en ráfaga y restaura en una sola pasada.
     */
    fun sendRealityCollapse(victim: Player, radius: Int) {
        if (!victim.isOnline) return

        val center = victim.location
        val affected = mutableListOf<Location>()
        val playerManager = PacketEvents.getAPI().playerManager

        // Generar ráfaga de paquetes
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val target = center.clone().add(x.toDouble(), -1.0, z.toDouble())
                affected.add(target)

                val pos = Vector3i(target.blockX, target.blockY, target.blockZ)
                playerManager.sendPacket(victim, WrapperPlayServerBlockChange(pos, 0))
            }
        }

        // Restauración optimizada
        factoryScope.launch {
            delay(750) // 15 ticks aprox
            if (victim.isOnline) {
                // --- ARREGLO: Reemplazado Dispatchers.Main por plugin.mainThread ---
                withContext(plugin.mainThread) {
                    affected.forEach { loc ->
                        victim.sendBlockChange(loc, loc.block.blockData)
                    }
                }
            }
        }
    }
}
