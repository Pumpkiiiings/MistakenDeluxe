package liric.mistaken.utils.hooks

import io.netty.buffer.Unpooled
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.Location
import org.bukkit.SoundCategory
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.Bukkit
import com.observer.api.model.ComponentAlignment
import com.observer.api.model.TextAlignment
import com.observer.paper.api.ObserverAPI
import liric.mistaken.Mistaken

object ObserverHook {

    val hasObserverPlugin: Boolean
        get() = Bukkit.getPluginManager().isPluginEnabled("ObserverPaper")

    fun hasObserver(player: Player): Boolean {
        if (!hasObserverPlugin) return false
        return try {
            ObserverAPI.isObserverPlayer(player)
        } catch (e: Throwable) {
            org.bukkit.Bukkit.getLogger().warning("[ObserverHook] Error in hasObserver:")
            e.printStackTrace()
            false
        }
    }

    fun createText(player: Player, id: String, content: String, alignment: ComponentAlignment, offsetX: Int, offsetY: Int, scale: Float, textAlignment: TextAlignment) {
        if (!hasObserverPlugin) return
        try {
            ObserverAPI.createText(player, id, content, alignment, offsetX, offsetY, scale, textAlignment)
        } catch (e: Exception) {
            org.bukkit.Bukkit.getLogger().warning("[ObserverHook] createText failed for ${player.name}: ${e.message}")
        }
    }

    fun createItem(player: Player, id: String, material: String, amount: Int, alignment: ComponentAlignment, offsetX: Int, offsetY: Int, scale: Float, textAlignment: TextAlignment) {
        if (!hasObserverPlugin) return
        try {
            ObserverAPI.createItem(player, id, material, amount, alignment, offsetX, offsetY, scale, textAlignment)
        } catch (e: Exception) {}
    }

    fun updateText(player: Player, id: String, content: String) {
        if (!hasObserverPlugin) return
        try {
            ObserverAPI.updateText(player, id, content)
        } catch (e: Exception) {}
    }

    fun clearHUD(player: Player) {
        if (!hasObserverPlugin) return
        try {
            ObserverAPI.clearHUD(player)
        } catch (e: Exception) {}
    }

    fun removeComponent(player: Player, id: String) {
        if (!hasObserverPlugin) return
        try {
            ObserverAPI.removeComponent(player, id)
        } catch (e: Exception) {}
    }

    fun playSound(player: Player, soundId: String, volume: Float = 1.0f, pitch: Float = 1.0f) {
        try {
            val namespace = if (soundId.contains(":")) soundId.substringBefore(":") else "minecraft"
            val id = if (soundId.contains(":")) soundId.substringAfter(":") else soundId
            val key = net.kyori.adventure.key.Key.key(namespace, id)
            val sound = net.kyori.adventure.sound.Sound.sound(key, net.kyori.adventure.sound.Sound.Source.RECORD, volume, pitch)
            player.playSound(sound)
        } catch (e: Exception) {
            player.playSound(player.location, soundId, org.bukkit.SoundCategory.RECORDS, volume, pitch)
        }
    }

    fun playSpatialSound(player: Player, soundId: String, x: Double, y: Double, z: Double, volume: Float = 1.0f, pitch: Float = 1.0f) {
        try {
            val namespace = if (soundId.contains(":")) soundId.substringBefore(":") else "minecraft"
            val id = if (soundId.contains(":")) soundId.substringAfter(":") else soundId
            val key = net.kyori.adventure.key.Key.key(namespace, id)
            val sound = net.kyori.adventure.sound.Sound.sound(key, net.kyori.adventure.sound.Sound.Source.RECORD, volume, pitch)
            player.playSound(sound, x, y, z)
        } catch (e: Exception) {
            player.playSound(Location(player.world, x, y, z), soundId, SoundCategory.RECORDS, volume, pitch)
        }
    }

    fun playEntitySound(player: Player, soundId: String, emitter: Entity, volume: Float = 1.0f, pitch: Float = 1.0f) {
        try {
            val namespace = if (soundId.contains(":")) soundId.substringBefore(":") else "minecraft"
            val id = if (soundId.contains(":")) soundId.substringAfter(":") else soundId
            val key = Key.key(namespace, id)
            val sound = Sound.sound(key, Sound.Source.RECORD, volume, pitch)
            player.playSound(sound, emitter)
        } catch (e: Exception) {
            player.playSound(emitter.location, soundId, SoundCategory.RECORDS, volume, pitch)
        }
    }

    fun stopSound(player: Player, soundId: String) {
        try {
            val namespace = if (soundId.contains(":")) soundId.substringBefore(":") else "minecraft"
            val id = if (soundId.contains(":")) soundId.substringAfter(":") else soundId
            val key = Key.key(namespace, id)
            player.stopSound(SoundStop.named(key))
        } catch (e: Exception) {
            player.stopSound(soundId, SoundCategory.RECORDS)
        }
    }

    fun stopAllSounds(player: Player) {
        try {
            player.stopSound(SoundStop.source(Sound.Source.RECORD))
        } catch (e: Exception) {
            player.stopAllSounds()
        }
    }

    // --- Screen Effects ---

    fun playScreenshake(player: Player, intensity: Float, durationTicks: Int) {
        if (!hasObserverPlugin) return
        try {
            com.observer.paper.api.PaperObserverScreenAPI.playScreenshake(player, intensity, durationTicks)
        } catch (e: Exception) {}
    }

    fun playScreenTint(player: Player, r: Int, g: Int, b: Int, alpha: Float, durationTicks: Int) {
        if (!hasObserverPlugin) return
        try {
            com.observer.paper.api.PaperObserverScreenAPI.playScreenTint(player, r, g, b, alpha, durationTicks)
        } catch (e: Exception) {}
    }

    // --- Animations ---

    private val plugin: Mistaken
        get() = JavaPlugin.getPlugin(Mistaken::class.java)

    fun getAnimation(player: Player, key: String, default: String): String {
        val config = plugin.config
        val session = plugin.sessionManager.getSession(player)
        
        val category: String
        val roleId: String
        
        if (session != null && session.isKiller(player.uniqueId)) {
            category = "killer"
            roleId = plugin.playerDataManager.getSelectedKiller(player.uniqueId)
        } else {
            category = "survivor"
            roleId = plugin.playerDataManager.getSelectedSurvivor(player.uniqueId)
        }
        
        // Specific class override (e.g. animations.slasher.run)
        var anim = config.getString("animations.$roleId.$key")
        if (anim != null && anim.isNotEmpty()) {
            return anim
        }
        
        // Category fallback (e.g. animations.killer.run)
        anim = config.getString("animations.$category.$key")
        if (anim != null && anim.isNotEmpty()) {
            return anim
        }
        
        // Legacy/Global fallback just in case
        anim = config.getString("animations.global.$key")
        if (anim != null && anim.isNotEmpty()) {
            return anim
        }

        return default
    }

    fun setTrueDarkness(player: Player, enabled: Boolean) {
        if (!hasObserverPlugin) return
        try {
            com.observer.paper.ObserverPaper.getInstance().environmentManager.setTrueDarkness(player, enabled)
        } catch (e: Exception) {
            // Ignore if Observer doesn't support environment manager yet
        }
    }
}

object ObserverEventListener : Listener {
    @EventHandler
    fun onLeftClick(event: com.observer.paper.api.events.ObserverPlayerLeftClickEvent) {
        if (!ObserverHook.hasObserverPlugin) return
        try {
            com.observer.paper.api.PaperObserverAnimationAPI.playAnimation(event.player, ObserverHook.getAnimation(event.player, "ataque", "ataque"))
        } catch (e: Exception) {}
    }

    @EventHandler
    fun onSprint(event: com.observer.paper.api.events.ObserverPlayerSprintEvent) {
        if (!ObserverHook.hasObserverPlugin) return
        try {
            if (event.isSprinting) {
                com.observer.paper.api.PaperObserverAnimationAPI.playAnimation(event.player, ObserverHook.getAnimation(event.player, "run", "run"))
            } else {
                com.observer.paper.api.PaperObserverAnimationAPI.stopAnimation(event.player)
            }
        } catch (e: Exception) {}
    }

    @EventHandler
    fun onWalk(event: com.observer.paper.api.events.ObserverPlayerWalkEvent) {
        if (!ObserverHook.hasObserverPlugin) return
        try {
            if (event.isWalking) {
                com.observer.paper.api.PaperObserverAnimationAPI.playAnimation(event.player, ObserverHook.getAnimation(event.player, "walk", "walk"))
            } else {
                com.observer.paper.api.PaperObserverAnimationAPI.stopAnimation(event.player)
            }
        } catch (e: Exception) {}
    }

    @EventHandler
    fun onJump(event: com.observer.paper.api.events.ObserverPlayerJumpEvent) {
        if (!ObserverHook.hasObserverPlugin) return
        try {
            com.observer.paper.api.PaperObserverAnimationAPI.playAnimation(event.player, ObserverHook.getAnimation(event.player, "jump", "jump"))
        } catch (e: Exception) {}
    }

    @EventHandler
    fun onIdle(event: com.observer.paper.api.events.ObserverPlayerIdleEvent) {
        if (!ObserverHook.hasObserverPlugin) return
        try {
            if (event.isIdle) {
                com.observer.paper.api.PaperObserverAnimationAPI.playAnimation(event.player, ObserverHook.getAnimation(event.player, "idle", "estiramiento"))
            } else {
                com.observer.paper.api.PaperObserverAnimationAPI.stopAnimation(event.player)
            }
        } catch (e: Exception) {}
    }
}
