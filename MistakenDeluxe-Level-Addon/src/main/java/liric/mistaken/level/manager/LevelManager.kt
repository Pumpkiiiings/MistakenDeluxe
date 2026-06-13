package liric.mistaken.level.manager

import liric.mistaken.api.level.event.PlayerExperienceGainEvent
import liric.mistaken.api.level.event.PlayerLevelUpEvent
import liric.mistaken.level.LevelAddonPlugin
import liric.mistaken.level.database.PlayerLevelData
import liric.mistaken.level.rewards.RewardRegistry
import net.kyori.adventure.text.minimessage.MiniMessage
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
                // Fire level up event on main thread
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val event = PlayerLevelUpEvent(player, oldLevel, level)
                    Bukkit.getPluginManager().callEvent(event)
                    
                    player.showTitle(net.kyori.adventure.title.Title.title(
                        MiniMessage.miniMessage().deserialize("<green><bold>NIVEL $level"),
                        MiniMessage.miniMessage().deserialize("<gray>Siguiente nivel es ${level + 1}"),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofMillis(3000), java.time.Duration.ofMillis(1000))
                    ))
                    player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                    
                    // Give rewards for each level up
                    for (l in (oldLevel + 1)..level) {
                        val rewards = plugin.levelConfig.getRewardsForLevel(l)
                        for (rewardStr in rewards) {
                            RewardRegistry.executeReward(plugin, player, rewardStr)
                        }
                    }

                    // Handle Prefix broadcast if a new prefix is reached exactly at this level
                    val prefixData = plugin.levelConfig.getPrefixForLevel(level)
                    if (prefixData != null && prefixData.requiredLevel in (oldLevel + 1)..level) {
                        if (prefixData.broadcast) {
                            val msg = "<green>Level Up!</green> ${player.name} reached level $level and unlocked the ${prefixData.display} prefix!"
                            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(msg))
                        }
                    }
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

        // Check level up via the unified method
        checkLevelUp(uuid, data)
    }

    fun checkLevelUp(uuid: UUID, data: PlayerLevelData? = null) {
        val playerData = data ?: cache[uuid] ?: return
        var levelUps = 0
        var currentLevel = playerData.level
        val maxLevel = plugin.levelConfig.maxLevel

        val mistakenCore = org.bukkit.Bukkit.getPluginManager().getPlugin("Mistaken") as liric.mistaken.Mistaken
        val stats = mistakenCore.statsManager.getStats(uuid)

        while (currentLevel < maxLevel) {
            val reqs = plugin.levelConfig.getRequirementsForLevel(currentLevel + 1)
            val requiredXp = reqs?.xp ?: getRequiredXp(currentLevel)

            val hasXp = playerData.experience >= requiredXp
            val hasKills = stats.kills.get() >= (reqs?.kills ?: 0)
            val hasWinsS = stats.winsSurvivor.get() >= (reqs?.winsSurvivor ?: 0)
            val hasWinsA = stats.winsAssassin.get() >= (reqs?.winsAssassin ?: 0)
            val hasWinsG = stats.totalWins >= (reqs?.winsGlobal ?: 0)
            val hasGens = stats.generatorsRepaired.get() >= (reqs?.generatorsRepaired ?: 0)

            if (hasXp && hasKills && hasWinsS && hasWinsA && hasWinsG && hasGens) {
                playerData.experience -= requiredXp
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
                plugin.repository.save(playerData)
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
