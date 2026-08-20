package liric.mistaken.scripting.effects.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualBlockDisplay
import liric.mistaken.scripting.effects.EffectHandle
import liric.mistaken.utils.worldViewers
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class FormationEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val location: Location,
    private val shape: String,
    private val count: Int,
    private val material: String,
    private val radius: Double,
    private val durationTicks: Int,
    private val onExpireCallback: ((Location) -> Unit)?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private val displays = mutableListOf<VirtualBlockDisplay>()
    private var scheduledTask: ScheduledTask? = null

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        plugin.server.regionScheduler.run(plugin, location, Consumer {
            if (!alive.get()) return@Consumer

            val mat = Material.matchMaterial(material) ?: Material.BEACON
            val worldViewers = location.worldViewers()
            
            for (i in 0 until count) {
                var x = 0.0
                var z = 0.0
                
                when (shape.lowercase()) {
                    "triangle", "circle" -> {
                        val angle = (i * Math.PI * 2) / count
                        x = radius * cos(angle)
                        z = radius * sin(angle)
                    }
                    "line" -> {
                        val offset = if (count > 1) (i - (count - 1) / 2.0) * radius else 0.0
                        x = offset
                    }
                    else -> {
                        // fallback circle
                        val angle = (i * Math.PI * 2) / count
                        x = radius * cos(angle)
                        z = radius * sin(angle)
                    }
                }

                val spawnLoc = location.clone().add(x, 2.0, z)
                val display = PacketFactory.displays.buildBlockDisplay(worldViewers, spawnLoc) { bd ->
                    bd.block = mat.createBlockData()
                    bd.transformation = Transformation(Vector3f(-0.5f, -0.5f, -0.5f), Quaternionf(), Vector3f(1f, 1f, 1f), Quaternionf())
                }
                displays.add(display)
            }

            scheduledTask = plugin.server.regionScheduler.runDelayed(plugin, location, Consumer { _ ->
                if (onExpireCallback != null) {
                    onExpireCallback.invoke(location)
                }
                cleanup()
            }, durationTicks.toLong())
        })
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            displays.forEach { it.remove() }
            displays.clear()
        }
    }
}
