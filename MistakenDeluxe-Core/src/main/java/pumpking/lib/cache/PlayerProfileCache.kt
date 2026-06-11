package pumpking.lib.cache

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import pumpking.lib.core.PumpkingLib
import java.util.UUID
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

abstract class PlayerProfileCache<V : Any>(
    val plugin: JavaPlugin,
    expirationMillis: Long = TimeUnit.HOURS.toMillis(1) // 1 hora de TTL por defecto si el jugador sale pero algo falla
) : CacheManager<UUID, V>(expirationMillis), Listener {


    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        PumpkingLib.log(PumpkingLib.LogCategory.CORE, "[Cache] Registered PlayerProfileCache listener.")
    }

    /**
     * Define how to load the profile from the database.
     */
    abstract fun loadProfile(uuid: UUID): V?

    /**
     * Define how to save the profile to the database.
     */
    abstract fun saveProfile(uuid: UUID, profile: V)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAsyncPreLogin(event: AsyncPlayerPreLoginEvent) {
        val uuid = event.uniqueId

        // Auto-load on login
        try {
            val profile = loadProfile(uuid)
            if (profile != null) {
                put(uuid, profile)
            }
        } catch (e: Exception) {
            PumpkingLib.logError(PumpkingLib.LogCategory.CORE, "[Cache] Failed to load profile for ${event.name}: ${e.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        val profile = remove(uuid)

        if (profile != null) {
            // Save on quit asynchronously
            pumpking.lib.task.PumpkingTask.ioScope.launch {
                try {
                    saveProfile(uuid, profile)
                } catch (e: Exception) {
                    PumpkingLib.logError(PumpkingLib.LogCategory.CORE, "[Cache] Failed to save profile for ${event.player.name} on quit: ${e.message}")
                }
            }
        }
    }

    override fun onExpire(key: UUID, value: V?) {
        if (value != null) {
            pumpking.lib.task.PumpkingTask.ioScope.launch {
                try {
                    saveProfile(key, value)
                } catch (e: Exception) {
                    PumpkingLib.logError(PumpkingLib.LogCategory.CORE, "[Cache] Failed to save expired profile for $key: ${e.message}")
                }
            }
        }
    }
}
