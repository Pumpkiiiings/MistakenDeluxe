package liric.mistaken.jsaddon

import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class JsAddonPlugin : JavaPlugin() {

    override fun onEnable() {
        logger.info("Mistaken JS Addon loading...")
        
        // Ensure Mistaken scripts directory exists
        val mistakenDir = File(server.updateFolderFile.parentFile, "Mistaken")
        val scriptsDir = File(mistakenDir, "scripts")
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs()
        }

        // Initialize engine and load scripts
        try {
            JsScriptEngine.init(logger, scriptsDir)
            logger.info("Mistaken JS Addon loaded successfully.")
        } catch (e: Exception) {
            logger.severe("Failed to initialize JS Script Engine: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDisable() {
        logger.info("Mistaken JS Addon disabled.")
    }
}
