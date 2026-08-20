package liric.mistaken.scripting.effects.orbit

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualBlockDisplay
import liric.mistaken.packet.fake.VirtualItemDisplay
import liric.mistaken.packet.fake.VirtualDisplay
import liric.mistaken.scripting.effects.EffectHandle
import liric.mistaken.scripting.effects.EffectRegistry
import liric.mistaken.utils.sessionViewers
import org.bukkit.Material
import org.bukkit.entity.Display
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

/**
 * Efecto de objetos orbitando alrededor de un jugador.
 * Generaliza showPhysicalTrail de Charlie/Entity303/Null/Romeo/Color.
 * Cada instancia posee su propio ScheduledTask (Folia-safe).
 */
class OrbitEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val player: Player,
    private val count: Int,
    private val materialNames: List<String>,
    private val isItem: Boolean,
    private val radius: Double,
    private val height: Double,
    private val rotationSpeed: Double,
    private val wobbleAmplitude: Double,
    private val wobbleFrequency: Double,
    private val glow: Boolean,
    private val maxTicks: Int?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private val displays = mutableListOf<VirtualDisplay>()
    private var scheduledTask: ScheduledTask? = null
    private var angle = 0.0
    private var tickCount = 0

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        val materials = if (materialNames.isEmpty()) listOf(Material.BEACON) else materialNames.map { Material.matchMaterial(it) ?: Material.BEACON }

        for (i in 0 until count) {
            val mat = materials[i % materials.size]
            val viewers = player.sessionViewers()

            if (isItem) {
                val display = PacketFactory.displays.buildItemDisplay(viewers, player.location) { id ->
                    id.setItemStack(ItemStack(mat))
                    id.transformation = Transformation(
                        Vector3f(0f, 0f, 0f),
                        Quaternionf(),
                        Vector3f(0.5f, 0.5f, 0.5f),
                        Quaternionf()
                    )
                    id.teleportDuration = 2
                    id.interpolationDuration = 2
                    if (glow) {
                        id.brightness = Display.Brightness(15, 15)
                    }
                }
                displays.add(display)
            } else {
                val display = PacketFactory.displays.buildBlockDisplay(viewers, player.location) { bd ->
                    bd.block = mat.createBlockData()
                    bd.transformation = Transformation(
                        Vector3f(-0.15f, -0.15f, -0.15f),
                        Quaternionf(),
                        Vector3f(0.3f, 0.3f, 0.3f),
                        Quaternionf()
                    )
                    bd.teleportDuration = 2
                    bd.interpolationDuration = 2
                    if (glow) {
                        bd.brightness = Display.Brightness(15, 15)
                    }
                }
                displays.add(display)
            }
        }

        scheduledTask = player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!alive.get() || !player.isOnline) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            if (maxTicks != null && tickCount >= maxTicks) {
                cleanup()
                task.cancel()
                return@Consumer
            }

            // Check world change
            if (displays.firstOrNull()?.world != player.world) {
                displays.forEach { it.remove() }
                displays.clear()
                cleanup()
                task.cancel()
                return@Consumer
            }

            val step = (2 * Math.PI) / displays.size
            val playerLoc = player.location

            for (i in displays.indices) {
                val display = displays[i]
                if (display.isValid) {
                    val currentAngle = angle + (step * i)
                    val x = radius * cos(currentAngle)
                    val z = radius * sin(currentAngle)
                    val y = height + (wobbleAmplitude * sin(currentAngle * wobbleFrequency))

                    val targetLoc = playerLoc.clone().add(x, y, z)
                    targetLoc.yaw = (currentAngle * 100).toFloat() % 360
                    targetLoc.pitch = (currentAngle * 50).toFloat() % 360
                    display.teleport(targetLoc)
                }
            }

            angle = (angle + rotationSpeed) % (Math.PI * 2)
            tickCount++
        }, null, 1L, 1L)
    }

    override fun stop() {
        cleanup()
    }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            scheduledTask = null
            displays.forEach { if (it.isValid) it.remove() }
            displays.clear()
        }
    }
}
