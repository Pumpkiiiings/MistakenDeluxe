package liric.mistaken.scripting.adapter

import liric.mistaken.scripting.api.ScriptPlayer
import net.kyori.adventure.text.Component
import org.bukkit.Sound
import org.bukkit.entity.Player

class BukkitPlayerAdapter(
    private val player: Player
) : BukkitEntityAdapter(player), ScriptPlayer {

    override fun is_online(): Boolean = player.isOnline

    override fun send_message(message: String) {
        player.sendMessage(Component.text(message))
    }

    override fun play_sound(sound: String, volume: Float, pitch: Float) {
        try {
            val bukkitSound = Sound.valueOf(sound.uppercase())
            player.playSound(player.location, bukkitSound, volume, pitch)
        } catch (e: Exception) {
            // Log sound error softly, don't crash
        }
    }

    override fun play_particle(particle: String, amount: Int) {
        try {
            val bukkitParticle = org.bukkit.Particle.valueOf(particle.uppercase())
            player.world.spawnParticle(bukkitParticle, player.location, amount)
        } catch (e: Exception) {
            // Ignore invalid particle
        }
    }

    override fun health(): Double = player.health

    override fun set_health(amount: Double) {
        var newHealth = amount
        val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        if (newHealth < 0.0) newHealth = 0.0
        if (newHealth > maxHealth) newHealth = maxHealth
        player.health = newHealth
    }

    override fun damage(amount: Double) {
        player.damage(amount)
    }

    override fun has_permission(permission: String): Boolean {
        return player.hasPermission(permission)
    }

    override fun set_scale(scale: Double) {
        player.getAttribute(org.bukkit.attribute.Attribute.SCALE)?.baseValue = scale
    }

    override fun reset_scale() {
        player.getAttribute(org.bukkit.attribute.Attribute.SCALE)?.baseValue = 1.0
    }

    override fun get_nearby_players(radius: Double): List<ScriptPlayer> {
        return player.getNearbyEntities(radius, radius, radius)
            .filterIsInstance<Player>()
            .filter { it.uniqueId != player.uniqueId } // Exclude self
            .map { BukkitPlayerAdapter(it) }
    }

    override fun ray_trace_player(distance: Double): ScriptPlayer? {
        val result = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, distance) {
            it is Player && it.uniqueId != player.uniqueId
        }
        val targetPlayer = result?.hitEntity as? Player
        return if (targetPlayer != null) BukkitPlayerAdapter(targetPlayer) else null
    }

    internal fun getPlayer(): Player = player
}

