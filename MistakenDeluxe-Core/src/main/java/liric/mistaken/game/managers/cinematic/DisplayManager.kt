package liric.mistaken.game.managers.cinematic

import liric.mistaken.Mistaken
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualBlockDisplay
import liric.mistaken.packet.fake.VirtualDisplay
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class DisplayManager(private val plugin: Mistaken) {

    private val activeDisplays = ConcurrentHashMap.newKeySet<VirtualDisplay>()

    fun clearDisplays() {
        activeDisplays.forEach { if (it.isValid) it.remove() }
        activeDisplays.clear()
    }

    fun spawnStaticBlock(loc: Location, mat: Material, scale: Float): VirtualBlockDisplay {
        val display = PacketFactory.displays.buildBlockDisplay(Bukkit.getOnlinePlayers().toList(), loc) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                Vector3f(-scale / 2, 0f, -scale / 2),
                Quaternionf(),
                Vector3f(scale, scale, scale),
                Quaternionf()
            )
        }
        activeDisplays.add(display)
        return display
    }

    fun spawnOrbitingBlock(center: Location, mat: Material, scale: Float, radius: Double, speed: Double, yOffset: Double) {
        val display = PacketFactory.displays.buildBlockDisplay(Bukkit.getOnlinePlayers().toList(), center) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                Vector3f(-scale / 2, 0f, -scale / 2),
                Quaternionf(),
                Vector3f(scale, scale, scale),
                Quaternionf()
            )
        }
        activeDisplays.add(display)
        var angle = Math.random() * Math.PI * 2
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid) {
                task.cancel(); return@Consumer
            }
            angle += speed
            val offsetLoc = center.clone().add(radius * cos(angle), 2.0 + yOffset + sin(angle * 2) * 0.5, radius * sin(angle))
            offsetLoc.setDirection(center.clone().add(0.0, 2.0, 0.0).toVector().subtract(offsetLoc.toVector()))
            display.teleport(offsetLoc)
        }, 1L, 1L)
    }

    fun spawnRotatingItem(loc: Location, mat: Material, scale: Float) {
        val display = PacketFactory.displays.buildItemDisplay(Bukkit.getOnlinePlayers().toList(), loc.clone().add(0.0, 1.2, 0.0)) { id ->
            id.setItemStack(ItemStack(mat))
            id.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(scale, scale, scale), Quaternionf())
        }
        activeDisplays.add(display)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid) {
                task.cancel(); return@Consumer
            }
            val t = display.transformation!!; t!!.leftRotation.rotateY(0.1f); display.transformation = t
        }, 1L, 1L)
    }

    fun spawnFallingItem(loc: Location, mat: Material) {
        val display = PacketFactory.displays.buildItemDisplay(Bukkit.getOnlinePlayers().toList(), loc) { id ->
            id.setItemStack(ItemStack(mat))
            id.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(0.8f, 0.8f, 0.8f), Quaternionf())
        }
        activeDisplays.add(display)
        var yOffset = 0.0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid || yOffset < -6.0) {
                display.remove(); task.cancel(); return@Consumer
            }
            yOffset -= 0.15
            val t = display.transformation!!; t!!.leftRotation.rotateX(0.2f).rotateY(0.1f)
            display.transformation = t; display.teleport(loc.clone().add(0.0, yOffset, 0.0))
        }, 1L, 1L)
    }

    fun spawnGlitchBlock(loc: Location, mat: Material) {
        val display = PacketFactory.displays.buildBlockDisplay(Bukkit.getOnlinePlayers().toList(), loc.clone().add(0.0, 1.2, 0.0)) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(Vector3f(-0.5f, 0f, -0.5f), Quaternionf(), Vector3f(1.0f, 1.0f, 1.0f), Quaternionf())
        }
        activeDisplays.add(display)
        var ticks = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid) {
                task.cancel(); return@Consumer
            }
            ticks++
            val s = if (ticks % 3 == 0) 1.5f else 0.8f
            display.transformation = Transformation(Vector3f(-s / 2, 0f, -s / 2), Quaternionf(), Vector3f(s, s, s), Quaternionf())
        }, 1L, 2L)
    }
}
