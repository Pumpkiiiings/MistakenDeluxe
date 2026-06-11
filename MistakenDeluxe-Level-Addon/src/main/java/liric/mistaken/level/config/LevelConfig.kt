package liric.mistaken.level.config

import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class LevelConfig(private val plugin: LevelAddonPlugin) {

    private lateinit var levelsConfig: YamlConfiguration
    private lateinit var rewardsConfig: YamlConfiguration

    var maxLevel = 100
        private set
    var defaultMultiplier = 500
        private set

    private val ranks = mutableListOf<RankData>()
    private val rewardsMap = mutableMapOf<Int, RewardData>()

    fun load() {
        val levelsFile = File(plugin.dataFolder, "levels.yml")
        if (!levelsFile.exists()) {
            plugin.saveResource("levels.yml", false)
        }
        levelsConfig = YamlConfiguration.loadConfiguration(levelsFile)

        val rewardsFile = File(plugin.dataFolder, "rewards.yml")
        if (!rewardsFile.exists()) {
            plugin.saveResource("rewards.yml", false)
        }
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile)

        // Load Settings
        maxLevel = levelsConfig.getInt("settings.max_level", 100)
        defaultMultiplier = levelsConfig.getInt("curves.default-multiplier", 500)

        // Load Ranks
        ranks.clear()
        val ranksSection = levelsConfig.getConfigurationSection("ranks")
        if (ranksSection != null) {
            for (key in ranksSection.getKeys(false)) {
                val rangeStr = ranksSection.getString("$key.range") ?: continue
                val prefix = ranksSection.getString("$key.prefix") ?: ""
                
                val parts = rangeStr.split("-")
                val min = parts[0].toIntOrNull() ?: 0
                val max = if (parts.size > 1) parts[1].toIntOrNull() ?: min else min
                
                ranks.add(RankData(min, max, prefix))
            }
        }

        // Load Rewards
        rewardsMap.clear()
        val rewardsSection = rewardsConfig.getConfigurationSection("rewards")
        if (rewardsSection != null) {
            for (key in rewardsSection.getKeys(false)) {
                val level = key.toIntOrNull() ?: continue
                val name = rewardsSection.getString("$key.reward_name", "Mystery Reward")!!
                val cmds = rewardsSection.getStringList("$key.commands")
                val msg = rewardsSection.getString("$key.message")
                rewardsMap[level] = RewardData(name, cmds, msg)
            }
        }
    }

    fun getPrefixForLevel(level: Int): String {
        return ranks.find { level in it.min..it.max }?.prefix ?: "<gray>[Player]</gray>"
    }

    fun getRewardForLevel(level: Int): RewardData? {
        return rewardsMap[level]
    }
    
    fun getNextRewardLevel(currentLevel: Int): Int {
        return rewardsMap.keys.sorted().firstOrNull { it > currentLevel } ?: -1
    }

    data class RankData(val min: Int, val max: Int, val prefix: String)
    data class RewardData(val name: String, val commands: List<String>, val message: String?)
}
