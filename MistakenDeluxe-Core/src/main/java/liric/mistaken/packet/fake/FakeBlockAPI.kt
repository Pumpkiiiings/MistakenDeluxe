package liric.mistaken.packet.fake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import com.github.retrooper.packetevents.util.Vector3i
import java.util.concurrent.ConcurrentHashMap

class FakeBlockAPI {

    private val blockDataCache = ConcurrentHashMap<String, Int>()
    private val materialCache = ConcurrentHashMap<Material, Int>()

    /**
     * Envía un bloque falso (Client-Side) a un player específico.
     * @param player El player que verá el bloque falso.
     * @param location La ubicación en el world.
     * @param material El material falso.
     */
    fun sendBlockChange(player: Player, location: Location, material: Material) {
        val globalId = materialCache.getOrPut(material) {
            SpigotConversionUtil.fromBukkitBlockData(material.createBlockData()).globalId
        }
        val packet = WrapperPlayServerBlockChange(
            Vector3i(location.blockX, location.blockY, location.blockZ),
            globalId
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    }

    /**
     * Igual que el anterior, pero con el BlockData completo.
     * Necesario para RESTAURAR un bloque real (conserva orientacion, waterlogged, etc.),
     * cosa que no se puede hacer solo con el Material.
     */
    fun sendBlockChange(player: Player, location: Location, data: BlockData) {
        val globalId = blockDataCache.getOrPut(data.asString) {
            SpigotConversionUtil.fromBukkitBlockData(data).globalId
        }
        val packet = WrapperPlayServerBlockChange(
            Vector3i(location.blockX, location.blockY, location.blockZ),
            globalId
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    }

    /**
     * Envía un cambio de bloque directamente con un globalId de PacketEvents.
     * Útil para optimizaciones extremas (ej. linternas) donde se pre-calcula.
     */
    fun sendBlockChange(player: Player, location: Location, globalId: Int) {
        val packet = WrapperPlayServerBlockChange(
            Vector3i(location.blockX, location.blockY, location.blockZ),
            globalId
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    }

    /**
     * Envía múltiples bloques falsos de forma optimizada.
     * @param player El player que verá los bloques.
     * @param blocks Un mapa de ubicaciones y materiales a cambiar.
     */
    fun sendMultiBlockChange(player: Player, blocks: Map<Location, Material>) {
        if (blocks.isEmpty()) return

        
        
        
        
        
        
        
        blocks.forEach { (loc, mat) ->
            sendBlockChange(player, loc, mat)
        }
    }
}
