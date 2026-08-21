package liric.mistaken.scripting.adapter

import liric.mistaken.scripting.api.ScriptWorld
import liric.mistaken.scripting.api.ScriptLocation
import org.bukkit.World
import org.bukkit.Sound

class BukkitWorldAdapter(
    private val world: World
) : ScriptWorld {

    override fun name(): String = world.name
    
    override fun is_day(): Boolean {
        val time = world.time
        return time in 0..12000
    }
    
    override fun time(): Long = world.time

    override fun play_sound(location: ScriptLocation, sound: String, volume: Float, pitch: Float) {
        if (location is BukkitLocationAdapter) {
            try {
                val bukkitSound = Sound.valueOf(sound.uppercase())
                world.playSound(location.getBukkitLocation(), bukkitSound, volume, pitch)
            } catch (e: Exception) {
                // Soft ignore
            }
        }
    }

    override fun spawn_particle(particle: String, location: ScriptLocation, count: Int) {
        if (location is BukkitLocationAdapter) {
            try {
                val bukkitParticle = org.bukkit.Particle.valueOf(particle.uppercase())
                world.spawnParticle(bukkitParticle, location.getBukkitLocation(), count)
            } catch (e: Exception) {
                // Soft ignore
            }
        }
    }
    
    override fun get_players(): Array<liric.mistaken.scripting.api.ScriptPlayer> {
        return world.players.map { BukkitPlayerAdapter(it) }.toTypedArray()
    }
}

