package liric.mistaken.scripting.effects.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualItemDisplay
import liric.mistaken.scripting.effects.EffectHandle
import liric.mistaken.utils.worldViewers
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class ProximityTrapEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val owner: Player,
    private val location: Location,
    private val model: String?,
    private val particle: String?,
    private val particleRadius: Double,
    private val triggerRadius: Double,
    private val durationTicks: Int,
    private val onTriggerCallback: ((Player) -> Unit)?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var scheduledTask: ScheduledTask? = null
    private var display: VirtualItemDisplay? = null
    private var tickCount = 0

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        if (model != null) {
            val mat = Material.matchMaterial(model) ?: Material.BEACON
            display = PacketFactory.displays.buildItemDisplay(location.worldViewers(), location.clone().add(0.0, 0.5, 0.0)) { id ->
                id.setItemStack(ItemStack(mat))
                id.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    Quaternionf(),
                    Vector3f(0.7f, 0.7f, 0.7f),
                    Quaternionf()
                )
            }
        }

        scheduledTask = plugin.server.regionScheduler.runAtFixedRate(plugin, location, Consumer { task ->
            if (!alive.get() || !owner.isOnline) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            if (tickCount >= durationTicks) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            // Partículas opcionales
            if (particle != null) {
                try {
                    val bukkitParticle = org.bukkit.Particle.valueOf(particle.uppercase())
                    val angle = tickCount * 0.4
                    val x = cos(angle) * particleRadius
                    val z = sin(angle) * particleRadius
                    location.world.spawnParticle(bukkitParticle, location.clone().add(x, 1.0, z), 1, 0.0, 0.0, 0.0, 0.0)
                } catch (e: Exception) {}
            }

            // Detección de proximidad
            if (onTriggerCallback != null) {
                val victim = location.world.getNearbyEntities(location, triggerRadius, triggerRadius, triggerRadius)
                    .filterIsInstance<Player>()
                    .firstOrNull { GameplayFunctions.isValidTarget(owner, it) }

                if (victim != null) {
                    victim.scheduler.run(plugin, Consumer { _ ->
                        onTriggerCallback.invoke(victim)
                    }, null)
                    cleanup()
                    task.cancel()
                    return@Consumer
                }
            }

            tickCount += 2 // Asumiendo 2 ticks si se configura a 2L
        }, 1L, 2L)
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            display?.remove()
        }
    }
}
