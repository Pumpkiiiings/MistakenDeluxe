package pumpking.lib.config

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired when a configuration file is reloaded or changed externally.
 * Listen to this event instead of polling or hardcoding specific module logic.
 */
class ConfigReloadEvent(val fileName: String) : Event() {
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLERS
    }
    
    override fun getHandlers(): HandlerList = HANDLERS
}
