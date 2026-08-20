package liric.mistaken.scripting.effects.projectile

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualItemDisplay
import liric.mistaken.scripting.effects.EffectHandle
import liric.mistaken.utils.sessionViewers
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Efecto de proyectil volador con detección de hit.
 * Generaliza habilidadBloqueHielo, habilidadInfeccionSistema, habilidadNetherStar.
 * Corre en regionScheduler del plugin (el proyectil puede cruzar regiones).
 */
class ProjectileEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val player: Player,
    private val materialName: String,
    private val isBlock: Boolean,
    private val speed: Double,
    private val maxTicks: Int,
    private val hitRadius: Double,
    private val trailParticleName: String?,
    private val trailParticleCount: Int,
    private val onHitCallback: ((Player) -> Unit)?,
    private val impactParticleName: String?,
    private val impactSoundName: String?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var display: VirtualItemDisplay? = null
    private var scheduledTask: ScheduledTask? = null
    private var tickCount = 0

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        val mat = Material.matchMaterial(materialName) ?: Material.NETHER_STAR
        val eyeLoc = player.eyeLocation
        val dir = eyeLoc.direction.multiply(speed)

        display = PacketFactory.displays.buildItemDisplay(
            player.sessionViewers(), eyeLoc
        ) { id ->
            id.setItemStack(ItemStack(mat))
            id.transformation = Transformation(
                Vector3f(), Quaternionf(),
                Vector3f(0.6f, 0.6f, 0.6f), Quaternionf()
            )
        }

        val d = display ?: return

        // Projectile runs on the display entity scheduler (wraps globalRegion, Folia-safe)
        scheduledTask = d.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!alive.get() || !d.isValid) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            if (tickCount >= maxTicks) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            d.teleport(d.location.add(dir))

            // Trail particles
            if (trailParticleName != null) {
                try {
                    val bukkitParticle = org.bukkit.Particle.valueOf(trailParticleName.uppercase())
                    d.world.spawnParticle(bukkitParticle, d.location, trailParticleCount, 0.1, 0.1, 0.1, 0.02)
                } catch (_: Exception) {}
            }

            // Hit detection - players
            val hit = d.world.getNearbyEntities(d.location, hitRadius, hitRadius, hitRadius)
                .filterIsInstance<Player>()
                .firstOrNull { it.uniqueId != ownerUuid && it.gameMode == org.bukkit.GameMode.SURVIVAL }

            // Hit detection - block
            val blockHit = d.location.block.type.isSolid

            if (hit != null || blockHit) {
                // Impact particles
                if (impactParticleName != null) {
                    try {
                        val p = org.bukkit.Particle.valueOf(impactParticleName.uppercase())
                        d.world.spawnParticle(p, d.location, 30, 0.5, 0.5, 0.5, 0.1)
                    } catch (_: Exception) {}
                }
                // Impact sound
                if (impactSoundName != null) {
                    try {
                        val s = Sound.valueOf(impactSoundName.uppercase())
                        d.world.playSound(d.location, s, 1.5f, 1f)
                    } catch (_: Exception) {}
                }
                // Callback — already running in the correct region thread (display entity's region)
                if (hit != null && onHitCallback != null) {
                    // Execute callback in the victim's entity scheduler to ensure Folia safety
                    hit.scheduler.run(plugin, Consumer { _ ->
                        onHitCallback.invoke(hit)
                    }, null)
                }
                cleanup()
                task.cancel()
            }

            tickCount++
        }, null, 1L, 1L)
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            scheduledTask = null
            display?.let { if (it.isValid) it.remove() }
            display = null
        }
    }
}
