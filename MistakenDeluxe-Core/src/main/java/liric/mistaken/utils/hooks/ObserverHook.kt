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
}
