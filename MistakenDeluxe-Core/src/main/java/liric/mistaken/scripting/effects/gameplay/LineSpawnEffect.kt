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
 * Generaliza TripleColmillo de Romeo, Charlie, NullKiller.
 * Corre usando runDelayed en la región del player atacante, que luego spawnea entidades.
 */
class LineSpawnEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val player: Player,
    private val count: Int,
    private val spacing: Double,
    private val delayTicks: Long,
    private val angles: List<Double>,
    private val snapToGround: Boolean,
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
                
                
                val task = plugin.server.regionScheduler.runDelayed(plugin, locToSpawn, Consumer { _ ->
                    if (!alive.get()) return@Consumer

                    var spawnY = locToSpawn.y
                    if (snapToGround) {
                        val world = locToSpawn.world
                        var y = locToSpawn.blockY
                        while (y > locToSpawn.blockY - 3 && !world.getBlockAt(locToSpawn.blockX, y - 1, locToSpawn.blockZ).type.isSolid) {
                            y--
                        }
                        while (y < locToSpawn.blockY + 3 && world.getBlockAt(locToSpawn.blockX, y, locToSpawn.blockZ).type.isSolid) {
                            y++
                        }
                        spawnY = y.toDouble()
                    }
                    val finalLoc = locToSpawn.clone().apply { y = spawnY }

                    if (!finalLoc.block.type.isSolid) {
                        val fangs = finalLoc.world.spawn(finalLoc, EvokerFangs::class.java)
                        
                        
                        if (onHitCallback != null) {
                            finalLoc.world.getNearbyEntities(finalLoc, 1.5, 1.5, 1.5)
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
