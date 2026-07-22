package liric.mistaken.utils.misc

import fr.skytasul.glowingentities.GlowingEntities
import org.bukkit.ChatColor
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SafeGlowingManager(plugin: Plugin) {

    val glowingEntities: GlowingEntities = GlowingEntities(plugin)

    // Key: targetEntityId -> Map(viewerUUID -> ChatColor)
    private val activeGlows = ConcurrentHashMap<Int, ConcurrentHashMap<UUID, ChatColor>>()

    fun setGlowing(target: Entity, viewer: Player, color: ChatColor) {
        val targetMap = activeGlows.computeIfAbsent(target.entityId) { ConcurrentHashMap() }
        val currentColor = targetMap[viewer.uniqueId]

        if (currentColor != color) {
            try {
                glowingEntities.setGlowing(target, viewer, color)
                targetMap[viewer.uniqueId] = color
            } catch (_: Exception) {
            }
        }
    }

    fun unsetGlowing(target: Entity, viewer: Player) {
        unsetGlowing(target.entityId, viewer)
    }

    fun unsetGlowing(targetEntityId: Int, viewer: Player) {
        val targetMap = activeGlows[targetEntityId] ?: return
        if (targetMap.remove(viewer.uniqueId) != null) {
            try {
                glowingEntities.unsetGlowing(targetEntityId, viewer)
            } catch (_: Exception) {
            }
            if (targetMap.isEmpty()) {
                activeGlows.remove(targetEntityId)
            }
        }
    }

    fun disable() {
        activeGlows.clear()
        try {
            glowingEntities.disable()
        } catch (_: Exception) {
        }
    }
}
