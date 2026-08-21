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
            if (!viewer.isOnline || !target.isValid) return
            try {
                glowingEntities.setGlowing(target, viewer, color)
                targetMap[viewer.uniqueId] = color
            } catch (e: ReflectiveOperationException) {
                liric.mistaken.MistakenLib.logError(liric.mistaken.MistakenLib.LogCategory.CORE, "Failed to set glowing for ${target.entityId} to ${viewer.name}: ${e.message}")
            }
        }
    }

    fun unsetGlowing(target: Entity, viewer: Player) {
        unsetGlowing(target.entityId, viewer)
    }

    fun unsetGlowing(targetEntityId: Int, viewer: Player) {
        val targetMap = activeGlows[targetEntityId] ?: return
        if (targetMap.remove(viewer.uniqueId) != null) {
            if (viewer.isOnline) {
                try {
                    glowingEntities.unsetGlowing(targetEntityId, viewer)
                } catch (e: ReflectiveOperationException) {
                    liric.mistaken.MistakenLib.logError(liric.mistaken.MistakenLib.LogCategory.CORE, "Failed to unset glowing for $targetEntityId to ${viewer.name}: ${e.message}")
                }
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
