package liric.mistaken.scripting.effects.trail

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Efecto de trail de partículas al caminar.
 * Generaliza showTrail de Charlie/Entity303/Null/Color.
 * Usa PacketEvents para eficiencia, corre en player.scheduler (Folia-safe).
 */
class TrailEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val player: Player,
    private val particleName: String,
    private val dustR: Float?,
    private val dustG: Float?,
    private val dustB: Float?,
    private val dustSize: Float,
    private val offsetX: Float,
    private val offsetY: Float,
    private val offsetZ: Float,
    private val viewRadius: Double,
    private val onlyWhenMoving: Boolean,
    private val particleCount: Int,
    private val maxTicks: Int?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var scheduledTask: ScheduledTask? = null
    private var lastX = 0.0
    private var lastZ = 0.0
    private var tickCount = 0

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        lastX = player.location.x
        lastZ = player.location.z

        scheduledTask = player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!alive.get() || !player.isOnline) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            if (maxTicks != null && tickCount >= maxTicks) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            val loc = player.location

            // Skip if not moving and onlyWhenMoving
            if (onlyWhenMoving) {
                val dx = loc.x - lastX
                val dz = loc.z - lastZ
                if (dx * dx + dz * dz < 0.01) {
                    lastX = loc.x; lastZ = loc.z
                    tickCount++
                    return@Consumer
                }
            }
            lastX = loc.x; lastZ = loc.z

            val particleLoc = loc.clone().add(0.0, 1.0, 0.0)

            // Build particle - use DUST if color specified, else named particle
            val particle = if (dustR != null && dustG != null && dustB != null) {
                Particle(ParticleTypes.DUST, ParticleDustData(dustR, dustG, dustB, dustSize))
            } else {
                val type = try {
                    ParticleTypes.getByName("minecraft:${particleName.lowercase()}")
                } catch (_: Exception) { null } ?: ParticleTypes.END_ROD
                Particle(type)
            }

            val packet = WrapperPlayServerParticle(
                particle, false,
                Vector3d(particleLoc.x, particleLoc.y, particleLoc.z),
                Vector3f(offsetX, offsetY, offsetZ),
                0.02f, particleCount
            )

            val viewRadiusSq = viewRadius * viewRadius
            val mgr = PacketEvents.getAPI().playerManager
            loc.world.players.forEach { viewer ->
                if (viewer != player && viewer.location.distanceSquared(loc) < viewRadiusSq) {
                    mgr.sendPacket(viewer, packet)
                }
            }

            tickCount++
        }, null, 1L, 1L)
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            scheduledTask = null
        }
    }
}
