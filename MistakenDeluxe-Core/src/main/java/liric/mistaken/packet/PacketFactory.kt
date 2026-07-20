package liric.mistaken.packet

import liric.mistaken.packet.fake.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Factory central para la API de paquetes Client-Side de Mistaken.
 */
object PacketFactory {
    
    // Generador seguro de IDs para entidades falsas.
    // Usamos un número muy alto para evitar colisionar con IDs de entidades reales de Bukkit.
    private val fakeEntityIdCounter = AtomicInteger(2000000000)

    val blocks = FakeBlockAPI()
    val entities = FakeEntityAPI()
    val displays = VirtualDisplayAPI()
    val npcs = FakeNPCAPI()
    val glow = FakeGlowAPI()

    /**
     * Retorna una ID de entidad segura y única para esta sesión de servidor.
     */
    fun generateEntityId(): Int {
        return fakeEntityIdCounter.getAndIncrement()
    }
}
