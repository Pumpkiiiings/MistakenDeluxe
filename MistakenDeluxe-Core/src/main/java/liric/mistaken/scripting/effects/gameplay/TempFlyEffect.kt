package liric.mistaken.scripting.effects.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Efecto de vuelo temporal.
 * Habilita el vuelo del player y lo deshabilita automáticamente tras un tiempo
 * o si se llama a stop() (útil para cuando el killer cambia de rol).
 */
class TempFlyEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val player: Player,
    private val maxTicks: Int,
    private val flySpeed: Float
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var scheduledTask: ScheduledTask? = null
    private val originalSpeed = player.flySpeed

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        player.allowFlight = true
        player.isFlying = true
        player.flySpeed = flySpeed

        scheduledTask = player.scheduler.runDelayed(plugin, Consumer { task ->
            cleanup()
        }, null, maxTicks.toLong())
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            scheduledTask = null
            if (player.isOnline) {
                player.allowFlight = false
                player.isFlying = false
                player.flySpeed = originalSpeed
            }
        }
    }
}
