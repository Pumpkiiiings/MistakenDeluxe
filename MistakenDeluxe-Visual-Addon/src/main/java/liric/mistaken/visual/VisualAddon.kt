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

    override fun reloadConfig() {
        val configFile = java.io.File(dataFolder, "config.yml")
        if (!configFile.exists()) {
            super.reloadConfig()
            return
        }
        val testConfig = org.bukkit.configuration.file.YamlConfiguration()
        try {
            testConfig.load(configFile)
            super.reloadConfig()
        } catch (e: Exception) {
            logger.severe("==============================================")
            logger.severe("ERROR GRAVE EN config.yml DE VISUAL-ADDON:")
            logger.severe("Hay un error de sintaxis en el archivo (mira la línea abajo).")
            logger.severe(e.message)
            logger.severe("La configuración NO se ha recargado para protegerla.")
            logger.severe("Corrige el error y usa /visual reload de nuevo.")
            logger.severe("==============================================")
        }
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
