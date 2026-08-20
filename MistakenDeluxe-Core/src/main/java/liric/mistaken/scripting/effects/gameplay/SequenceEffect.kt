package liric.mistaken.scripting.effects.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class SequenceEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val location: Location,
    private val steps: List<Pair<Long, () -> Unit>>
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private val tasks = mutableListOf<ScheduledTask>()

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        steps.forEach { (delayTicks, action) ->
            val task = plugin.server.regionScheduler.runDelayed(plugin, location, Consumer { _ ->
                if (alive.get()) {
                    action.invoke()
                }
            }, delayTicks.coerceAtLeast(1L))
            tasks.add(task)
        }
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            tasks.forEach { it.cancel() }
            tasks.clear()
        }
    }
}
