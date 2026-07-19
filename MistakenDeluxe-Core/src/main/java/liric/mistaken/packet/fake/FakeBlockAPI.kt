package liric.mistaken.packet.fake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player

class FakeBlockAPI {

    /**
     * Envía un bloque falso (Client-Side) a un jugador específico.
     * @param player El jugador que verá el bloque falso.
     * @param location La ubicación en el mundo.
     * @param material El material falso.
     */
    fun sendBlockChange(player: Player, location: Location, material: Material) {
        val blockState = SpigotConversionUtil.fromBukkitBlockData(material.createBlockData())
        val packet = WrapperPlayServerBlockChange(
            SpigotConversionUtil.fromBukkitLocation(location).position,
            blockState.globalId
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    }

    /**
     * Envía múltiples bloques falsos de forma optimizada.
     * @param player El jugador que verá los bloques.
     * @param blocks Un mapa de ubicaciones y materiales a cambiar.
     */
    fun sendMultiBlockChange(player: Player, blocks: Map<Location, Material>) {
        if (blocks.isEmpty()) return

        // PacketEvents WrapperPlayServerMultiBlockChange requiere agrupar por Chunk
        // Para simplificar esta API, simplemente enviamos múltiples block changes individuales 
        // si los bloques están esparcidos, o usar el wrapper oficial si están en un mismo chunk.
        // Como optimización genérica y fácil de usar, enviamos individuales. 
        // Nota: Para cambios masivos en el mismo chunk, se recomienda MultiBlockChange, 
        // pero para evitar cálculos de chunks, el bucle suele ser igual de rápido para menos de 500 bloques.
        
        blocks.forEach { (loc, mat) ->
            sendBlockChange(player, loc, mat)
        }
    }
}
