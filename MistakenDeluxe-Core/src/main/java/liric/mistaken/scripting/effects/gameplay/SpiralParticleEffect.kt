package liric.mistaken.scripting.effects.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class SpiralParticleEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val location: Location,
    private val particle1: String,
    private val particle2: String,
    private val maxTicks: Int,
    private val onFinishCallback: ((Location) -> Unit)?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var scheduledTask: ScheduledTask? = null
    private var tickCount = 0

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        val world = location.world
        val p1 = Particle.valueOf(particle1.uppercase())
        val p2 = Particle.valueOf(particle2.uppercase())

        scheduledTask = plugin.server.regionScheduler.runAtFixedRate(plugin, location, Consumer { task ->
            if (!alive.get()) {
                task.cancel()
                return@Consumer
            }

            if (tickCount >= maxTicks) {
                if (onFinishCallback != null) {
                    onFinishCallback.invoke(location)
                }
                cleanup()
                task.cancel()
                return@Consumer
            }

            try {
                val angle = tickCount * 0.5
                val radius = 2.0 - (tickCount * 0.05)
                val x = radius * cos(angle)
                val z = radius * sin(angle)
                val y = tickCount * 0.1

                world.spawnParticle(p1, location.clone().add(x, y, z), 5, 0.1, 0.1, 0.1, 0.0)
                world.spawnParticle(p2, location.clone().add(-x, y, -z), 2, 0.1, 0.1, 0.1, 0.0)
            } catch (e: Exception) {}

            tickCount++
        }, 1L, 1L)
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
        }
    }
}
