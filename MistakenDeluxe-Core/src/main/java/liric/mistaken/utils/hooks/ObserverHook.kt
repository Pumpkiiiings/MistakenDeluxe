package liric.mistaken.utils.hooks

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import com.observer.api.model.ComponentAlignment
import com.observer.api.model.TextAlignment
import com.observer.paper.api.ObserverAPI

object ObserverHook {

    private val hasObserverPlugin: Boolean
        get() = Bukkit.getPluginManager().isPluginEnabled("ObserverPaper")

    fun hasObserver(player: Player): Boolean {
        if (!hasObserverPlugin) return false
        return try {
            ObserverAPI.isObserverPlayer(player)
        } catch (e: NoClassDefFoundError) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun createText(player: Player, id: String, content: String, alignment: ComponentAlignment, offsetX: Int, offsetY: Int, scale: Float, textAlignment: TextAlignment) {
        if (!hasObserverPlugin) return
        try {
            ObserverAPI.createText(player, id, content, alignment, offsetX, offsetY, scale, textAlignment)
        } catch (e: Exception) {}
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
        if (!hasObserverPlugin) return
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
        if (!hasObserverPlugin) return
        try {
            val namespace = if (soundId.contains(":")) soundId.substringBefore(":") else "minecraft"
            val id = if (soundId.contains(":")) soundId.substringAfter(":") else soundId
            val key = net.kyori.adventure.key.Key.key(namespace, id)
            val sound = net.kyori.adventure.sound.Sound.sound(key, net.kyori.adventure.sound.Sound.Source.RECORD, volume, pitch)
            player.playSound(sound, x, y, z)
        } catch (e: Exception) {
            player.playSound(org.bukkit.Location(player.world, x, y, z), soundId, org.bukkit.SoundCategory.RECORDS, volume, pitch)
        }
    }

    fun playEntitySound(player: Player, soundId: String, emitter: org.bukkit.entity.Entity, volume: Float = 1.0f, pitch: Float = 1.0f) {
        if (!hasObserverPlugin) return
        try {
            val namespace = if (soundId.contains(":")) soundId.substringBefore(":") else "minecraft"
            val id = if (soundId.contains(":")) soundId.substringAfter(":") else soundId
            val key = net.kyori.adventure.key.Key.key(namespace, id)
            val sound = net.kyori.adventure.sound.Sound.sound(key, net.kyori.adventure.sound.Sound.Source.RECORD, volume, pitch)
            player.playSound(sound, emitter)
        } catch (e: Exception) {
            player.playSound(emitter.location, soundId, org.bukkit.SoundCategory.RECORDS, volume, pitch)
        }
    }

    fun stopSound(player: Player, soundId: String) {
        if (!hasObserverPlugin) return
        try {
            val namespace = if (soundId.contains(":")) soundId.substringBefore(":") else "minecraft"
            val id = if (soundId.contains(":")) soundId.substringAfter(":") else soundId
            val key = net.kyori.adventure.key.Key.key(namespace, id)
            player.stopSound(net.kyori.adventure.sound.SoundStop.named(key))
        } catch (e: Exception) {
            player.stopSound(soundId, org.bukkit.SoundCategory.RECORDS)
        }
    }

    fun stopAllSounds(player: Player) {
        if (!hasObserverPlugin) return
        try {
            player.stopSound(net.kyori.adventure.sound.SoundStop.source(net.kyori.adventure.sound.Sound.Source.RECORD))
        } catch (e: Exception) {
            player.stopAllSounds()
        }
    }
}
