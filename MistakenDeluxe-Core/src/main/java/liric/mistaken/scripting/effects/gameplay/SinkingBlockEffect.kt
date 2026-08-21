package liric.mistaken.scripting.effects.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class SinkingBlockEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val location: Location,
    private val material: String,
    private val sinkTicks: Int,
    private val durationTicks: Int,
    private val onRemoveCallback: ((Location) -> Unit)?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var blockDisplay: BlockDisplay? = null
    private var removeTask: ScheduledTask? = null

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        plugin.server.regionScheduler.run(plugin, location, Consumer {
            if (!alive.get()) return@Consumer

            val mat = Material.matchMaterial(material) ?: Material.OBSIDIAN
            
            
            val spawnLoc = location.clone().add(0.0, 2.0, 0.0)
            
            blockDisplay = location.world.spawn(spawnLoc, BlockDisplay::class.java) { bd ->
                bd.block = mat.createBlockData()
                bd.transformation = Transformation(Vector3f(-1.5f, -1.5f, -1.5f), Quaternionf(), Vector3f(3f, 3f, 3f), Quaternionf())
                bd.teleportDuration = sinkTicks
            }

            
            
            plugin.server.regionScheduler.runDelayed(plugin, location, Consumer { _ ->
                if (!alive.get()) return@Consumer
                blockDisplay?.teleport(location.clone().subtract(0.0, 2.0, 0.0)) 
            }, 5L)

            
            removeTask = plugin.server.regionScheduler.runDelayed(plugin, location, Consumer { _ ->
                if (onRemoveCallback != null) {
                    onRemoveCallback.invoke(location)
                }
                cleanup()
            }, durationTicks.toLong())
        })
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            removeTask?.cancel()
            blockDisplay?.let {
                if (it.isValid) it.remove()
            }
        }
    }
}
