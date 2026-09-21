package liric.mistaken.visual

import liric.mistaken.api.MistakenProvider
import liric.mistaken.visual.listeners.ChatListener
import liric.mistaken.visual.tasks.TabListTask
import org.bukkit.plugin.java.JavaPlugin

class VisualAddon : JavaPlugin() {
    companion object {
        lateinit var instance: VisualAddon
            private set
    }

    override fun onEnable() {
        instance = this
        
        saveDefaultConfig()
        
        // Register chat listener
        if (config.getBoolean("chat.enabled", true)) {
            server.pluginManager.registerEvents(ChatListener(), this)
            logger.info("Chat Formatting enabled!")
        }
        
        // Register TabList task
        if (config.getBoolean("tablist.enabled", true)) {
            val interval = config.getLong("tablist.update-interval", 20L)
            server.scheduler.runTaskTimer(this, TabListTask(), 0L, interval)
            logger.info("TabList formatting enabled with interval $interval ticks.")
        }
        
        lifecycleManager.registerEventHandler(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS) { event ->
            event.registrar().register("visual", "Reload visual addon config", liric.mistaken.visual.commands.VisualCommand())
        }
        
        logger.info("MistakenDeluxe-Visual-Addon enabled successfully!")
    }

    override fun onDisable() {
        server.scheduler.cancelTasks(this)
        logger.info("MistakenDeluxe-Visual-Addon disabled!")
    }
}
