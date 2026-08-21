package liric.mistaken.scripting.effects.gameplay

import liric.mistaken.Mistaken
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * Funciones de gameplay compartidas, expuestas a Lua como funciones globales.
 * Todos los métodos operan sobre Bukkit Player directamente (solo llamados desde Kotlin).
 */
object GameplayFunctions {

    private val plugin: Mistaken
        get() = JavaPlugin.getPlugin(Mistaken::class.java)

    /**
     * Inflige daño a través del CombatManager (respeta las reglas de sesión).
     */
    fun damage(victim: Player, amount: Double = 3.0, sourceName: String? = null) {
        plugin.combatManager.takeDamage(victim, amount, sourceName)
    }

    /**
     * Aplica un efecto de poción.
     * @param effectName nombre del PotionEffectType (lowercase OK)
     * @param amplifier amplificador (0-based)
     * @param durationTicks duración en ticks
     */
    fun applyEffect(victim: Player, effectName: String, amplifier: Int, durationTicks: Int) {
        val type = PotionEffectType.getByName(effectName.uppercase()) ?: return
        val clampedAmp = amplifier.coerceIn(0, 10)
        val clampedDur = durationTicks.coerceIn(1, 6000)
        victim.addPotionEffect(PotionEffect(type, clampedDur, clampedAmp))
    }

    /**
     * Aplica knockback a un player desde una dirección.
     */
    fun knockback(victim: Player, source: Player, horizontalForce: Double, verticalForce: Double) {
        val dir = victim.location.toVector()
            .subtract(source.location.toVector())
            .normalize()
            .multiply(horizontalForce.coerceIn(0.0, 5.0))
            .setY(verticalForce.coerceIn(0.0, 3.0))
        victim.velocity = dir
    }

    /**
     * Devuelve players cercanos en modo SURVIVAL (filtra al propio player).
     */
    fun nearbyPlayers(player: Player, radius: Double): List<Player> {
        val clampedRadius = radius.coerceIn(0.5, 50.0)
        return player.world.getNearbyEntities(player.location, clampedRadius, clampedRadius, clampedRadius)
            .filterIsInstance<Player>()
            .filter { it.uniqueId != player.uniqueId }
    }

    /**
     * Devuelve players cercanos que sean válidos como target.
     * Usa la misma lógica que CoreKiller.isValidTarget.
     */
    fun nearbyValidTargets(player: Player, radius: Double): List<Player> {
        return nearbyPlayers(player, radius).filter { isValidTarget(player, it) }
    }

    /**
     * Comprueba si un player es un objetivo válido.
     * Lógica: el target debe estar en SURVIVAL, no ser espectador, y no ser killer.
     */
    fun isValidTarget(player: Player, target: Player): Boolean {
        if (target.uniqueId == player.uniqueId) return false
        if (target.gameMode != GameMode.SURVIVAL) return false
        if (!target.isOnline) return false
        // Delegate to killerManager if available
        return !plugin.killerManager.isKiller(target)
    }

    /**
     * Reproduce un sonido en una ubicación.
     */
    fun playSound(player: Player, soundName: String, volume: Float, pitch: Float) {
        try {
            val sound = Sound.valueOf(soundName.uppercase())
            player.world.playSound(player.location, sound, volume.coerceIn(0f, 3f), pitch.coerceIn(0.1f, 2f))
        } catch (_: Exception) {}
    }

    /**
     * Reproduce un sonido en una ubicación arbitraria del world.
     */
    fun playSoundAt(location: org.bukkit.Location, soundName: String, volume: Float, pitch: Float) {
        try {
            val sound = Sound.valueOf(soundName.uppercase())
            location.world?.playSound(location, sound, volume.coerceIn(0f, 3f), pitch.coerceIn(0.1f, 2f))
        } catch (_: Exception) {}
    }

    /**
     * Envía un message traducido al player, resolviendo la key contra
     * MessageService — mismo sistema que usan los killers Kotlin.
     */
    fun sendTranslated(player: Player, key: String) {
        player.sendMessage(liric.mistaken.config.engine.core.MessageService.getComponent(null, key))
    }

    /**
     * Spawna una explosión puntual de partículas en una ubicación.
     */
    fun spawnParticleBurst(
        location: org.bukkit.Location,
        particleName: String,
        count: Int,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        speed: Double
    ) {
        try {
            val particle = org.bukkit.Particle.valueOf(particleName.uppercase())
            location.world?.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed)
        } catch (_: Exception) {}
    }
}
