package liric.mistaken.utils.scripting

import liric.mistaken.Mistaken
import liric.mistaken.utils.hooks.ObserverHook
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

object SkillAPI {
    
    private val plugin = Mistaken.instance

    /**
     * Ejecuta código después de X ticks (1 segundo = 20 ticks)
     */
    fun delay(ticks: Long, action: () -> Unit) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable { action() }, ticks)
    }

    /**
     * Dibuja una estrella de partículas plana en el suelo
     */
    fun drawStar(player: Player, color: Color, radio: Double, puntas: Int) {
        val loc = player.location.add(0.0, 0.1, 0.0)
        val dust = Particle.DustOptions(color, 1.0f)
        for (i in 0 until puntas) {
            val a = i * Math.PI * 2 / puntas
            val na = (i + 2) * Math.PI * 2 / puntas
            val p1 = loc.clone().add(cos(a) * radio, 0.0, sin(a) * radio)
            val p2 = loc.clone().add(cos(na) * radio, 0.0, sin(na) * radio)
            val dir = p2.toVector().subtract(p1.toVector())
            val len = dir.length()
            dir.normalize()
            var d = 0.0

            plugin.server.regionScheduler.run(plugin, loc, Consumer { _ ->
                while (d < len) {
                    player.world.spawnParticle(Particle.DUST, p1.clone().add(dir.clone().multiply(d)), 1, dust)
                    d += 0.3
                }
            })
        }
    }

    fun playScreenTint(player: Player, r: Int, g: Int, b: Int, alpha: Float, durationTicks: Int) {
        ObserverHook.playScreenTint(player, r, g, b, alpha, durationTicks)
    }

    fun playScreenshake(player: Player, intensity: Float, durationTicks: Int) {
        ObserverHook.playScreenshake(player, intensity, durationTicks)
    }

    fun playSound(player: Player, sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
        player.playSound(player.location, sound, volume, pitch)
    }
    
    fun playSound(player: Player, customSoundId: String, volume: Float = 1.0f, pitch: Float = 1.0f) {
        ObserverHook.playSound(player, customSoundId, volume, pitch)
    }
}
