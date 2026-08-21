package liric.mistaken

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin
import liric.mistaken.config.engine.core.ConfigManager

import liric.mistaken.utils.scoreboard.ScoreboardManager
import liric.mistaken.config.engine.core.MessageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

object MistakenLib {
    lateinit var plugin: JavaPlugin
        private set

    private val supervisorJob = SupervisorJob()
    val ioScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    val asyncScope = CoroutineScope(Dispatchers.Default + supervisorJob)

    val cacheExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Mistaken-Cache-Cleaner")
    }

    enum class LogCategory(val prefix: String) {
        CORE("<gradient:#ff8c00:#ff0080>[Mistaken]</gradient>"),
        CONFIG("<gradient:#ff8c00:#ff0080>[Mistaken]</gradient>"),
        SCOREBOARD("<gradient:#ff8c00:#ff0080>[Mistaken]</gradient>")
    }

    /**
     * Centralized Logger for MistakenLib.
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
     * Initializes the entire MistakenLib core.
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
            ScoreboardManager.init(plugin)
            log(LogCategory.SCOREBOARD, "INIT OK")
        } catch (e: Exception) {
            logError(LogCategory.SCOREBOARD, "FAIL SAFE ERROR - ${e.message}")
        }

        try {
            MessageService.init()
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


        ScoreboardManager.shutdown()
        log(LogCategory.SCOREBOARD, "CLEANUP EVENT OK")

        log(LogCategory.CORE, "SERVICES OK")

        shutdownTasks()
        log(LogCategory.CORE, "TASKS OK")

        log(LogCategory.CORE, "Shutdown complete.")
    }

    fun shutdownTasks() {
        supervisorJob.cancel("Mistaken shutdown")
        cacheExecutor.shutdown()
    }
}
