package liric.mistaken.utils.hooks

import org.bukkit.Bukkit
import org.bukkit.entity.Player

object ObserverHook {

    private val hasObserverPlugin: Boolean
        get() = Bukkit.getPluginManager().isPluginEnabled("ObserverPaper")

    fun hasObserver(player: Player): Boolean {
        if (!hasObserverPlugin) return false
        return try {
            com.observer.paper.api.ObserverAPI.isObserverPlayer(player)
        } catch (e: NoClassDefFoundError) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun createText(player: Player, id: String, content: String, alignment: com.observer.api.model.ComponentAlignment, offsetX: Int, offsetY: Int, scale: Float, textAlignment: com.observer.api.model.TextAlignment) {
        if (!hasObserverPlugin) return
        try {
            com.observer.paper.api.ObserverAPI.createText(player, id, content, alignment, offsetX, offsetY, scale, textAlignment)
        } catch (e: Exception) {}
    }

    fun createItem(player: Player, id: String, material: String, amount: Int, alignment: com.observer.api.model.ComponentAlignment, offsetX: Int, offsetY: Int, scale: Float, textAlignment: com.observer.api.model.TextAlignment) {
        if (!hasObserverPlugin) return
        try {
            com.observer.paper.api.ObserverAPI.createItem(player, id, material, amount, alignment, offsetX, offsetY, scale, textAlignment)
        } catch (e: Exception) {}
    }

    fun updateText(player: Player, id: String, content: String) {
        if (!hasObserverPlugin) return
        try {
            com.observer.paper.api.ObserverAPI.updateText(player, id, content)
        } catch (e: Exception) {}
    }

    fun clearHUD(player: Player) {
        if (!hasObserverPlugin) return
        try {
            com.observer.paper.api.ObserverAPI.clearHUD(player)
        } catch (e: Exception) {}
    }

    fun removeComponent(player: Player, id: String) {
        if (!hasObserverPlugin) return
        try {
            com.observer.paper.api.ObserverAPI.removeComponent(player, id)
        } catch (e: Exception) {}
    }
}
