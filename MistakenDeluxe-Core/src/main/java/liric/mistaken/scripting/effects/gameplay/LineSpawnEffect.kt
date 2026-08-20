package liric.mistaken.scripting.effects.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import org.bukkit.entity.EvokerFangs
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Spawn de líneas de colmillos (EvokerFangs) con delay.
 * Generaliza TripleColmillo de Romeo, Charlie, NullAsesino.
 * Corre usando runDelayed en la región del jugador atacante, que luego spawnea entidades.
 */
class LineSpawnEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val player: Player,
    private val count: Int,
    private val spacing: Double,
    private val delayTicks: Long,
    private val angles: List<Double>,
    private val onHitCallback: ((Player) -> Unit)?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private val hitPlayers = mutableSetOf<UUID>()
    private val tasks = mutableListOf<ScheduledTask>()

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        val startLoc = player.location

        angles.forEach { offsetAngle ->
            val direction = startLoc.direction.clone()
                .rotateAroundY(Math.toRadians(offsetAngle))
                .setY(0.0)
                .normalize()
            
            val currentLoc = startLoc.clone()

            for (i in 0 until count) {
                currentLoc.add(direction.clone().multiply(spacing))
                val locToSpawn = currentLoc.clone()

                val ticks = (i * delayTicks + 1).coerceAtLeast(1L)
                
                // Programar el spawn en la región destino
                val task = plugin.server.regionScheduler.runDelayed(plugin, locToSpawn, Consumer { _ ->
                    if (!alive.get()) return@Consumer

                    if (!locToSpawn.block.type.isSolid) {
                        val fangs = locToSpawn.world.spawn(locToSpawn, EvokerFangs::class.java)
                        
                        // Hit detection local a los fangs
                        if (onHitCallback != null) {
                            locToSpawn.world.getNearbyEntities(locToSpawn, 1.5, 1.5, 1.5)
                                .filterIsInstance<Player>()
                                .forEach { victim ->
                                    if (GameplayFunctions.isValidTarget(player, victim) && hitPlayers.add(victim.uniqueId)) {
                                        victim.scheduler.run(plugin, Consumer { _ ->
                                            onHitCallback.invoke(victim)
                                        }, null)
                                    }
                                }
                        }
                    }
                }, ticks)
                
                tasks.add(task)
            }
        }
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            tasks.forEach { it.cancel() }
            tasks.clear()
            hitPlayers.clear()
        }
    }
}
