package liric.mistaken.packet.fake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleType
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Player

class FakeParticleAPI {

    /**
     * Envía una partícula visual simple a un grupo de players usando PacketEvents.
     */
    fun sendParticle(
        viewers: Collection<Player>,
        loc: Location,
        particleType: ParticleType<*>,
        amount: Int = 1,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        offsetZ: Float = 0f,
        speed: Float = 0f
    ) {
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val type = particleType as ParticleType<com.github.retrooper.packetevents.protocol.particle.data.ParticleData>
        val packet = WrapperPlayServerParticle(
            Particle(type), false, pos, Vector3f(offsetX, offsetY, offsetZ), speed, amount
        )
        val pm = PacketEvents.getAPI().playerManager
        viewers.forEach { pm.sendPacket(it, packet) }
    }

    /**
     * Envía una partícula de polvo (REDSTONE) coloreada a un grupo de players usando PacketEvents.
     */
    fun sendDustParticle(
        viewers: Collection<Player>,
        loc: Location,
        color: Color,
        size: Float = 1.0f,
        amount: Int = 1,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        offsetZ: Float = 0f
    ) {
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val r = color.red / 255.0f
        val g = color.green / 255.0f
        val b = color.blue / 255.0f
        val dustData = ParticleDustData(r, g, b, size)
        
        val packet = WrapperPlayServerParticle(
            Particle(ParticleTypes.DUST, dustData), false, pos, Vector3f(offsetX, offsetY, offsetZ), 0.0f, amount
        )
        val pm = PacketEvents.getAPI().playerManager
        viewers.forEach { pm.sendPacket(it, packet) }
    }
}
