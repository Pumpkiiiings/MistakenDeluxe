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
     * EnvÃ­a un bloque falso (Client-Side) a un jugador especÃ­fico.
     * @param player El jugador que verÃ¡ el bloque falso.
     * @param location La ubicaciÃ³n en el mundo.
     * @param material El material falso.
     */
    fun sendBlockChange(player: Player, location: Location, material: Material) {
        val blockState = SpigotConversionUtil.fromBukkitBlockData(material.createBlockData())
        val packet = WrapperPlayServerBlockChange(
            com.github.retrooper.packetevents.util.Vector3i(location.blockX, location.blockY, location.blockZ),
            blockState.globalId
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    }

    /**
     * EnvÃ­a mÃºltiples bloques falsos de forma optimizada.
     * @param player El jugador que verÃ¡ los bloques.
     * @param blocks Un mapa de ubicaciones y materiales a cambiar.
     */
    fun sendMultiBlockChange(player: Player, blocks: Map<Location, Material>) {
        if (blocks.isEmpty()) return

        // PacketEvents WrapperPlayServerMultiBlockChange requiere agrupar por Chunk
        // Para simplificar esta API, simplemente enviamos mÃºltiples block changes individuales 
        // si los bloques estÃ¡n esparcidos, o usar el wrapper oficial si estÃ¡n en un mismo chunk.
        // Como optimizaciÃ³n genÃ©rica y fÃ¡cil de usar, enviamos individuales. 
        // Nota: Para cambios masivos en el mismo chunk, se recomienda MultiBlockChange, 
        // pero para evitar cÃ¡lculos de chunks, el bucle suele ser igual de rÃ¡pido para menos de 500 bloques.
        
        blocks.forEach { (loc, mat) ->
            sendBlockChange(player, loc, mat)
        }
    }
}

