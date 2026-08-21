package liric.mistaken.scripting.effects.dash

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Efecto de dash/impulso con detección de colisión.
 * Generaliza abilityDashCodigo, abilityAdminDash, abilityVividTrace.
 * Corre en player.scheduler (Folia-safe).
 */
class DashEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val player: Player,
    private val speed: Double,
    private val maxTicks: Int,
    private val hitRadius: Double,
    private val stopOnBlock: Boolean,
    private val trailParticleName: String?,
    private val onHitCallback: ((Player) -> Unit)?,
    private val onBlockHitCallback: (() -> Unit)?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var scheduledTask: ScheduledTask? = null
    private var tickCount = 0
    private val hitPlayers = mutableSetOf<UUID>()

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        scheduledTask = player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!alive.get() || !player.isOnline) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            if (tickCount >= maxTicks) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            
            val dir = player.location.direction.normalize().multiply(speed)
            player.velocity = dir

            
            if (trailParticleName != null) {
                try {
                    val p = org.bukkit.Particle.valueOf(trailParticleName.uppercase())
                    player.world.spawnParticle(p, player.location, 5, 0.2, 0.5, 0.2, 0.05)
                } catch (_: Exception) {}
            }

            
            if (stopOnBlock) {
                val checkLoc = player.location.clone().add(dir.clone().multiply(0.8))
                if (checkLoc.block.type.isSolid) {
                    
                    onBlockHitCallback?.invoke()
                    cleanup()
                    task.cancel()
                    return@Consumer
                }
            }

            
            if (onHitCallback != null) {
                player.world.getNearbyEntities(player.location, hitRadius, hitRadius, hitRadius)
                    .filterIsInstance<Player>()
                    .forEach { victim ->
                        if (victim.uniqueId != ownerUuid
                            && victim.gameMode == GameMode.SURVIVAL
                            && hitPlayers.add(victim.uniqueId)
                        ) {
                            
                            victim.scheduler.run(plugin, Consumer { _ ->
                                onHitCallback.invoke(victim)
                            }, null)
                        }
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
            hitPlayers.clear()
        }
    }
}
