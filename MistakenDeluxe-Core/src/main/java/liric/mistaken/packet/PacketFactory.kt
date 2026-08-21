package liric.mistaken.packet

import liric.mistaken.packet.fake.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Factory central para la API de paquetes Client-Side de Mistaken.
 */
object PacketFactory {
    
    
    
    private val fakeEntityIdCounter = AtomicInteger(2000000000)

    val blocks = FakeBlockAPI()
    val entities = FakeEntityAPI()
    val displays = VirtualDisplayAPI()
    val npcs = FakeNPCAPI()
    val glow = FakeGlowAPI()
    val particles = FakeParticleAPI()

    /**
     * Retorna una ID de entidad segura y única para esta sesión de servidor.
     */
    fun generateEntityId(): Int {
        return fakeEntityIdCounter.getAndIncrement()
    }
}
