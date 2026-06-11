package pumpking.lib.core

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin
import pumpking.lib.config.ConfigManager
import pumpking.lib.cooldown.CooldownManager
import pumpking.lib.scoreboard.ScoreboardManager

object PumpkingLib {
    lateinit var plugin: JavaPlugin
        private set

    enum class LogCategory(val prefix: String) {
        CORE("<gradient:#ff8c00:#ff0080>[PumpkingCore]</gradient>"),
        CONFIG("<gradient:#00ff87:#60efff>[PumpkingConfig]</gradient>"),
        SCOREBOARD("<gradient:#8e2de2:#4a00e0>[PumpkingScoreboard]</gradient>"),
        COOLDOWN("<gradient:#f12711:#f5af19>[PumpkingCooldown]</gradient>")
    }

    /**
     * Centralized Logger for PumpkingLib.
     */
    fun log(category: LogCategory, message: String) {
        val formatted = "${category.prefix} <gray>$message</gray>"
        plugin.componentLogger.info(MiniMessage.miniMessage().deserialize(formatted))
    }

    fun logError(category: LogCategory, message: String) {
        val formatted = "${category.prefix} <red>$message</red>"
        plugin.componentLogger.error(MiniMessage.miniMessage().deserialize(formatted))
    }

    /**
     * Initializes the entire PumpkingLib core.
     * This must be called inside onEnable().
     */
    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
        
        log(LogCategory.CORE, "INIT OK - Starting modules...")
        
        // Initialize internal systems
        try {
            ConfigManager.init(plugin)
            log(LogCategory.CONFIG, "INIT OK")
        } catch (e: Exception) {
            logError(LogCategory.CONFIG, "FAIL SAFE ERROR - ${e.message}")
        }

        try {
            CooldownManager.init(plugin)
            log(LogCategory.COOLDOWN, "INIT OK")
        } catch (e: Exception) {
            logError(LogCategory.COOLDOWN, "FAIL SAFE ERROR - ${e.message}")
        }

        try {
            ScoreboardManager.init(plugin)
            log(LogCategory.SCOREBOARD, "INIT OK")
        } catch (e: Exception) {
            logError(LogCategory.SCOREBOARD, "FAIL SAFE ERROR - ${e.message}")
        }
        
        try {
            pumpking.lib.service.PumpkingServiceManager.init(plugin)
            log(LogCategory.CORE, "SERVICES INIT OK")
        } catch (e: Exception) {
            logError(LogCategory.CORE, "FAIL SAFE ERROR - ${e.message}")
        }

        log(LogCategory.CORE, "All core modules successfully initialized.")
    }

    /**
     * Safely shuts down tasks and clears memory.
     * Call this inside onDisable().
     */
    fun shutdown() {
        log(LogCategory.CORE, "SHUTDOWN OK - Stopping modules...")
        
        ConfigManager.shutdown()
        log(LogCategory.CONFIG, "CLEANUP EVENT OK")

        CooldownManager.shutdown()
        log(LogCategory.COOLDOWN, "CLEANUP EVENT OK")

        ScoreboardManager.shutdown()
        log(LogCategory.SCOREBOARD, "CLEANUP EVENT OK")
        
        pumpking.lib.service.PumpkingServiceManager.shutdown()
        log(LogCategory.CORE, "SERVICES OK")

        log(LogCategory.CORE, "Shutdown complete.")
    }
}
