package liric.mistaken.scripting.effects.music

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import liric.mistaken.utils.hooks.ObserverHook
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Efecto de música ambiental.
 * Generaliza la música de CharlieJazz, ColorAndElectricity.
 * Corre en el scheduler del player y actualiza el sonido para todos los players.
 */
class AmbientMusicEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val player: Player,
    private val soundId: String,
    private val intervalTicks: Long,
    private val volume: Float
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var scheduledTask: ScheduledTask? = null

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        stopOldMusic()

        scheduledTask = player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!alive.get() || !player.isOnline) {
                cleanup()
                task.cancel()
                return@Consumer
            }
            
            Bukkit.getOnlinePlayers().forEach { p ->
                ObserverHook.stopSound(p, soundId)
                ObserverHook.playEntitySound(p, soundId, player, volume, 1.0f)
            }
        }, null, 1L, intervalTicks.coerceAtLeast(20L))
    }

    override fun stop() { cleanup() }

    private fun stopOldMusic() {
        Bukkit.getOnlinePlayers().forEach { p ->
            ObserverHook.stopSound(p, soundId)
        }
    }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            scheduledTask = null
            stopOldMusic()
        }
    }
}
