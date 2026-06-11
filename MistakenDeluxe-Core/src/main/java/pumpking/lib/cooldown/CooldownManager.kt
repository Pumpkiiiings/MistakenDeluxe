package pumpking.lib.cooldown

import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CooldownManager {

    // Map<PlayerUUID, Map<CooldownKey, Cooldown>>
    internal val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Cooldown>>()
    private var scheduledTask: java.util.concurrent.ScheduledFuture<*>? = null

    fun init(plugin: JavaPlugin) {
        val cleanerTask = CooldownCleanerTask()
        scheduledTask = pumpking.lib.task.PumpkingTask.cacheExecutor.scheduleAtFixedRate(cleanerTask, 2, 2, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun shutdown() {
        scheduledTask?.cancel(false)
        cooldowns.clear()
    }

    fun setCooldown(uuid: UUID, key: String, durationMs: Long) {
        val playerCooldowns = cooldowns.getOrPut(uuid) { ConcurrentHashMap() }
        playerCooldowns[key] = Cooldown(key, System.currentTimeMillis() + durationMs)
    }

    fun isOnCooldown(uuid: UUID, key: String): Boolean {
        val playerCooldowns = cooldowns[uuid] ?: return false
        val cooldown = playerCooldowns[key] ?: return false

        return if (System.currentTimeMillis() < cooldown.expiresAt) {
            true
        } else {
            playerCooldowns.remove(key)
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(uuid)
            }
            false
        }
    }

    fun getRemainingMillis(uuid: UUID, key: String): Long {
        val playerCooldowns = cooldowns[uuid] ?: return 0L
        val cooldown = playerCooldowns[key] ?: return 0L

        val remaining = cooldown.expiresAt - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }
}
