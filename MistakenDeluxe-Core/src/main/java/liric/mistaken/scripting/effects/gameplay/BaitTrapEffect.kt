package liric.mistaken.scripting.effects.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class BaitTrapEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val owner: Player,
    private val location: Location,
    private val markerItem: String?,
    private val orbitParticle: String?,
    private val triggerRadius: Double,
    private val maxTicks: Int,
    private val onTriggerCallback: ((Player) -> Unit)?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var scheduledTask: ScheduledTask? = null
    private var armorStand: ArmorStand? = null
    private var tickCount = 0

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        val world = location.world
        val spawnLoc = location.block.location.add(0.5, 0.1, 0.5)
        
        // ArmorStand is an entity, must be spawned sync to its region.
        
        plugin.server.regionScheduler.run(plugin, spawnLoc, Consumer {
            if (!alive.get()) return@Consumer
            
            armorStand = world.spawn(spawnLoc, ArmorStand::class.java) { asEntity ->
                asEntity.isInvisible = true
                asEntity.setGravity(false)
                asEntity.isMarker = true
                if (markerItem != null) {
                    val mat = Material.matchMaterial(markerItem) ?: Material.BEACON
                    asEntity.equipment.helmet = ItemStack(mat)
                }
            }

            scheduledTask = plugin.server.regionScheduler.runAtFixedRate(plugin, spawnLoc, Consumer { task ->
                if (!alive.get() || !owner.isOnline || armorStand?.isValid == false) {
                    cleanup()
                    task.cancel()
                    return@Consumer
                }

                if (tickCount >= maxTicks) {
                    cleanup()
                    task.cancel()
                    return@Consumer
                }

                if (orbitParticle != null) {
                    try {
                        val angle = tickCount * 0.4
                        val x = cos(angle) * 0.7
                        val z = sin(angle) * 0.7
                        val bukkitParticle = Particle.valueOf(orbitParticle.uppercase())
                        world.spawnParticle(bukkitParticle, spawnLoc.clone().add(x, 1.0, z), 1, 0.0, 0.0, 0.0, 0.0)
                    } catch (e: Exception) {}
                }

                if (onTriggerCallback != null) {
                    val victim = world.getNearbyPlayers(spawnLoc, triggerRadius).firstOrNull { GameplayFunctions.isValidTarget(owner, it) }

                    if (victim != null) {
                        victim.scheduler.run(plugin, Consumer { _ ->
                            onTriggerCallback.invoke(victim)
                        }, null)
                        cleanup()
                        task.cancel()
                        return@Consumer
                    }
                }

                tickCount += 2
            }, 1L, 2L)
        })
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            armorStand?.let {
                if (it.isValid) it.remove()
            }
        }
    }
}
