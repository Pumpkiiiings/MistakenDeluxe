package liric.mistaken.utils.visuals

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleType
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import liric.mistaken.Mistaken
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

object ParticleShapesUtils {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)

    /**
     * Envía un paquete de partículas a todos los jugadores en un radio de 50 bloques.
     * Ideal para efectos visuales sin sobrecargar el servidor (Bukkit API bypass).
     */
    fun broadcastParticle(loc: Location, type: ParticleType<*>, offsetX: Float = 0f, offsetY: Float = 0f, offsetZ: Float = 0f, count: Int = 1, speed: Float = 0f) {
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val packet = WrapperPlayServerParticle(Particle(type), false, pos, Vector3f(offsetX, offsetY, offsetZ), speed, count)
        
        loc.world?.players?.forEach { viewer ->
            if (viewer.location.distanceSquared(loc) < 2500.0) { // 50 bloques
                PacketEvents.getAPI().playerManager.sendPacket(viewer, packet)
            }
        }
    }

    /**
     * Dibuja una hélice doble (ADN) de partículas ascendentes.
     */
    fun drawDnaHelix(center: Location, type: ParticleType<*> = ParticleTypes.SOUL_FIRE_FLAME, radius: Double = 1.0, height: Double = 3.0) {
        plugin.server.regionScheduler.run(plugin, center, Consumer { _ ->
            val steps = 40
            for (i in 0..steps) {
                val y = (i.toDouble() / steps) * height
                val angle = i * 0.5
                val x1 = cos(angle) * radius
                val z1 = sin(angle) * radius
                val x2 = cos(angle + Math.PI) * radius
                val z2 = sin(angle + Math.PI) * radius
                
                broadcastParticle(center.clone().add(x1, y, z1), type)
                broadcastParticle(center.clone().add(x2, y, z2), type)
            }
        })
    }

    /**
     * Dibuja un anillo (Onda Expansiva) de partículas que se agranda dinámicamente.
     */
    fun drawShockwave(center: Location, type: ParticleType<*> = ParticleTypes.SONIC_BOOM, maxRadius: Double = 5.0) {
        var radius = 0.0
        val centerClone = center.clone().add(0.0, 0.1, 0.0)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (radius > maxRadius) {
                task.cancel()
                return@Consumer
            }
            val points = (radius * 15).toInt().coerceAtLeast(10)
            for (i in 0 until points) {
                val angle = (2 * Math.PI * i) / points
                val x = cos(angle) * radius
                val z = sin(angle) * radius
                broadcastParticle(centerClone.clone().add(x, 0.0, z), type)
            }
            radius += 0.5
        }, 1L, 1L)
    }

    /**
     * Dibuja un vórtice (Agujero Negro) que absorbe partículas desde afuera hacia el centro.
     */
    fun drawVortex(center: Location, type: ParticleType<*> = ParticleTypes.PORTAL, radius: Double = 5.0, height: Double = 3.0) {
        var currentRadius = radius
        var currentHeight = height
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (currentRadius <= 0.1) {
                task.cancel()
                return@Consumer
            }
            val points = 15
            for (i in 0 until points) {
                val angle = (2 * Math.PI * i) / points + currentRadius
                val x = cos(angle) * currentRadius
                val z = sin(angle) * currentRadius
                broadcastParticle(center.clone().add(x, currentHeight, z), type)
            }
            currentRadius -= 0.2
            currentHeight -= (height / (radius / 0.2))
        }, 1L, 1L)
    }

    /**
     * Dibuja el símbolo de infinito matemático (Curva de Lissajous).
     */
    fun drawInfinityMark(center: Location, type: ParticleType<*> = ParticleTypes.ENCHANT, size: Double = 2.0) {
        plugin.server.regionScheduler.run(plugin, center, Consumer { _ ->
            val steps = 60
            for (i in 0..steps) {
                val t = (2 * Math.PI * i) / steps
                val x = size * cos(t)
                val z = size * sin(2 * t) / 2
                broadcastParticle(center.clone().add(x, 0.1, z), type)
            }
        })
    }

    /**
     * Dibuja una esfera hueca tridimensional alrededor de un centro.
     */
    fun drawSphere(center: Location, type: ParticleType<*> = ParticleTypes.END_ROD, radius: Double = 2.0, density: Int = 10) {
        plugin.server.regionScheduler.run(plugin, center, Consumer { _ ->
            for (i in 0..density) {
                val phi = Math.PI * i / density
                for (j in 0 until (density * 2)) {
                    val theta = 2 * Math.PI * j / (density * 2)
                    val x = radius * sin(phi) * cos(theta)
                    val y = radius * cos(phi)
                    val z = radius * sin(phi) * sin(theta)
                    broadcastParticle(center.clone().add(x, y + radius, z), type)
                }
            }
        })
    }

    /**
     * Dibuja un corazón en 3D usando curvas paramétricas.
     */
    fun drawHeart(center: Location, type: ParticleType<*> = ParticleTypes.HEART, size: Double = 1.0) {
        plugin.server.regionScheduler.run(plugin, center, Consumer { _ ->
            val steps = 50
            for (i in 0..steps) {
                val t = (2 * Math.PI * i) / steps
                val x = size * 16 * Math.pow(sin(t), 3.0) / 16.0
                val y = size * (13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)) / 16.0
                broadcastParticle(center.clone().add(x, y + 1.0, 0.0), type)
            }
        })
    }

    /**
     * Dibuja una estrella plana de N puntas.
     */
    fun drawStar(center: Location, type: ParticleType<*> = ParticleTypes.FIREWORK, radius: Double = 2.0, points: Int = 5) {
        plugin.server.regionScheduler.run(plugin, center, Consumer { _ ->
            for (i in 0 until points) {
                val a1 = Math.toRadians((i * 360.0 / points) - 90)
                val a2 = Math.toRadians(((i + 2) * 360.0 / points) - 90)

                val p1 = center.clone().add(radius * cos(a1), 0.0, radius * sin(a1))
                val p2 = center.clone().add(radius * cos(a2), 0.0, radius * sin(a2))

                val distance = p1.distance(p2)
                val vector = p2.toVector().subtract(p1.toVector()).normalize().multiply(0.2)

                var length = 0.0
                val currentP = p1.clone()
                while (length < distance) {
                    broadcastParticle(currentP, type)
                    currentP.add(vector)
                    length += 0.2
                }
            }
        })
    }

    /**
     * Dibuja un tornado ascendente expansivo.
     */
    fun drawTornado(center: Location, type: ParticleType<*> = ParticleTypes.CAMPFIRE_COSY_SMOKE, height: Double = 5.0, maxRadius: Double = 3.0) {
        var currentY = 0.0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (currentY > height) {
                task.cancel()
                return@Consumer
            }
            val radius = (currentY / height) * maxRadius + 0.5 // Crece con la altura
            val points = (radius * 10).toInt().coerceAtLeast(5)
            for (i in 0 until points) {
                val angle = (2 * Math.PI * i) / points + currentY // Rotación añadida
                val x = cos(angle) * radius
                val z = sin(angle) * radius
                broadcastParticle(center.clone().add(x, currentY, z), type)
            }
            currentY += 0.2
        }, 1L, 1L)
    }

    /**
     * Dibuja alas angélicas o demoníacas estáticas detrás del jugador, alineadas a su mirada.
     */
    fun drawWings(player: org.bukkit.entity.Player, type: ParticleType<*> = ParticleTypes.FLAME) {
        val loc = player.location
        val yaw = Math.toRadians(loc.yaw.toDouble() + 90) // +90 para que estén a la espalda
        
        // Offset detrás del jugador
        val backOffset = 0.3
        val backX = cos(yaw) * backOffset
        val backZ = sin(yaw) * backOffset
        
        val center = loc.clone().add(-backX, 1.2, -backZ)
        val vectorYaw = center.direction.normalize()
        val vectorRight = vectorYaw.clone().crossProduct(org.bukkit.util.Vector(0, 1, 0)).normalize()
        
        plugin.server.regionScheduler.run(plugin, center, Consumer { _ ->
            // Patrón básico de ala (V shape con puntos)
            val wingPoints = listOf(
                Pair(0.2, 0.0), Pair(0.4, 0.2), Pair(0.6, 0.4), Pair(0.8, 0.6), Pair(1.0, 0.8), // Top edge
                Pair(0.3, -0.2), Pair(0.5, -0.1), Pair(0.7, 0.1), Pair(0.9, 0.3), // Mid
                Pair(0.1, -0.4), Pair(0.2, -0.6), Pair(0.3, -0.8) // Bottom edge
            )
            
            for (point in wingPoints) {
                val xOffset = point.first
                val yOffset = point.second
                
                // Ala derecha
                val rightWing = center.clone().add(vectorRight.clone().multiply(xOffset)).add(0.0, yOffset, 0.0)
                broadcastParticle(rightWing, type)
                
                // Ala izquierda
                val leftWing = center.clone().add(vectorRight.clone().multiply(-xOffset)).add(0.0, yOffset, 0.0)
                broadcastParticle(leftWing, type)
            }
        })
    }
}
