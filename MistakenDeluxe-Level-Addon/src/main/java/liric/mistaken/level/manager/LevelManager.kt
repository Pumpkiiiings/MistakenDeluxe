package liric.mistaken.level.manager

import liric.mistaken.api.level.event.PlayerExperienceGainEvent
import liric.mistaken.api.level.event.PlayerLevelUpEvent
import liric.mistaken.level.LevelAddonPlugin
import liric.mistaken.level.database.PlayerLevelData
import org.bukkit.Bukkit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LevelManager(private val plugin: LevelAddonPlugin) {

    private val cache = ConcurrentHashMap<UUID, PlayerLevelData>()

    // Exposed to providers
    fun getLevel(uuid: UUID): Int {
        return cache[uuid]?.level ?: 1
    }

    fun setLevel(uuid: UUID, level: Int) {
        val data = cache[uuid] ?: return
        val oldLevel = data.level
        data.level = level
        
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            plugin.repository.save(data)
        })

        if (oldLevel != level) {
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                // We should fire level up event on main thread if possible, or async if required.
                // Assuming sync is safer for API consumers:
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val event = PlayerLevelUpEvent(player, oldLevel, level)
                    Bukkit.getPluginManager().callEvent(event)
                    
                    // TODO: Give rewards based on config
                })
            }
        }
    }

    fun getExperience(uuid: UUID): Long {
        return cache[uuid]?.experience ?: 0L
    }

    fun getTotalExperience(uuid: UUID): Long {
        return cache[uuid]?.totalExperience ?: 0L
    }

    fun setExperience(uuid: UUID, amount: Long) {
        val data = cache[uuid] ?: return
        data.experience = amount
        // Notice: we don't recalculate total_experience dynamically on set, just on add.
        
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            plugin.repository.save(data)
        })
    }

    fun addExperience(uuid: UUID, amount: Long, reason: PlayerExperienceGainEvent.GainReason = PlayerExperienceGainEvent.GainReason.UNKNOWN) {
        val player = Bukkit.getPlayer(uuid) ?: return
        val data = cache[uuid] ?: return

        // Fire event
        val event = PlayerExperienceGainEvent(player, amount, reason)
        Bukkit.getPluginManager().callEvent(event)

        if (event.isCancelled || event.amount <= 0) return

        data.experience += event.amount
        data.totalExperience += event.amount

        // Check level up
        var levelUps = 0
        var currentLevel = data.level
        var maxLevel = plugin.levelConfig.maxLevel

        while (currentLevel < maxLevel) {
            val requiredXp = getRequiredXp(currentLevel)
            if (data.experience >= requiredXp) {
                data.experience -= requiredXp
                currentLevel++
                levelUps++
            } else {
                break
            }
        }

        if (levelUps > 0) {
            setLevel(uuid, currentLevel)
        } else {
            // Just save experience asynchronously
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                plugin.repository.save(data)
            })
        }
    }

    fun loadPlayer(uuid: UUID) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            var data = plugin.repository.load(uuid)
            if (data == null) {
                data = PlayerLevelData(uuid, 1, 0L, 0L, 0)
                plugin.repository.save(data)
            }
            cache[uuid] = data
        })
    }

    fun savePlayer(uuid: UUID) {
        val data = cache.remove(uuid)
        if (data != null) {
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                plugin.repository.save(data)
            })
        }
    }

    fun saveAllSync() {
        for (data in cache.values) {
            plugin.repository.save(data)
        }
    }

    fun getRequiredXp(level: Int): Long {
        return (plugin.levelConfig.defaultMultiplier * level).toLong()
    }
}
