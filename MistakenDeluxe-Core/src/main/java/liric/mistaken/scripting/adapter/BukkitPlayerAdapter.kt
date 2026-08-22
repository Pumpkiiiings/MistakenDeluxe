package liric.mistaken.scripting.adapter

import liric.mistaken.scripting.api.HasLocation
import liric.mistaken.scripting.api.ScriptPlayer
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player

class BukkitPlayerAdapter(
    private val player: Player
) : BukkitEntityAdapter(player), ScriptPlayer, HasLocation {

    override fun bukkitLocation(): Location = player.location

    override fun is_online(): Boolean = player.isOnline

    override fun send_message(message: String) {
        player.sendMessage(Component.text(message))
    }

    override fun play_sound(sound: String, volume: Float, pitch: Float) {
        try {
            val bukkitSound = Sound.valueOf(sound.uppercase())
            player.playSound(player.location, bukkitSound, volume, pitch)
        } catch (e: Exception) {
            
        }
    }

    override fun play_particle(particle: String, amount: Int) {
        try {
            val bukkitParticle = org.bukkit.Particle.valueOf(particle.uppercase())
            player.world.spawnParticle(bukkitParticle, player.location, amount)
        } catch (e: Exception) {
            
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
    
    override fun max_health(): Double {
        return player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
    }
    
    override fun is_killer(): Boolean {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(liric.mistaken.Mistaken::class.java)
        val session = plugin.sessionManager.getSession(player) ?: return false
        return session.isKiller(player.uniqueId)
    }
    
    override fun is_survivor(): Boolean {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(liric.mistaken.Mistaken::class.java)
        val session = plugin.sessionManager.getSession(player) ?: return false
        return !session.isKiller(player.uniqueId) && session.currentState == liric.mistaken.game.enums.GameState.INGAME
    }

    internal fun getPlayer(): Player = player
}
