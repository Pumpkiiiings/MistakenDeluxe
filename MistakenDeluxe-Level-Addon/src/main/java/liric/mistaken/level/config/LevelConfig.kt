package liric.mistaken.level.config

import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class LevelConfig(private val plugin: LevelAddonPlugin) {

    lateinit var config: YamlConfiguration
        private set

    var maxLevel = 100
        private set
    var defaultMultiplier = 500
        private set

    private val prefixesMap = mutableMapOf<Int, PrefixData>()
    private val levelRewards = mutableMapOf<Int, List<String>>()

    fun load() {
        val levelsFile = File(plugin.dataFolder, "levels.yml")
        if (!levelsFile.exists()) {
            plugin.saveResource("levels.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(levelsFile)

        maxLevel = config.getInt("settings.max_level", 100)
        defaultMultiplier = config.getInt("curves.default-multiplier", 500)

        // Load Prefixes
        prefixesMap.clear()
        val prefixesSection = config.getConfigurationSection("prefixes")
        if (prefixesSection != null) {
            for (key in prefixesSection.getKeys(false)) {
                val level = key.toIntOrNull() ?: continue
                val display = prefixesSection.getString("$key.display") ?: ""
                val color = prefixesSection.getString("$key.color") ?: "WHITE"
                val broadcast = prefixesSection.getBoolean("$key.broadcast", false)
                prefixesMap[level] = PrefixData(level, display, color, broadcast)
            }
        }

        // Load Levels/Rewards
        levelRewards.clear()
        val levelsSection = config.getConfigurationSection("levels")
        if (levelsSection != null) {
            for (key in levelsSection.getKeys(false)) {
                val level = key.toIntOrNull() ?: continue
                val rewards = levelsSection.getStringList("$key.rewards")
                levelRewards[level] = rewards
            }
        }
    }

    fun getPrefixForLevel(level: Int): PrefixData? {
        val matchingLevels = prefixesMap.keys.filter { it <= level }.sortedDescending()
        if (matchingLevels.isNotEmpty()) {
            return prefixesMap[matchingLevels.first()]
        }
        return null
    }

    fun getRewardsForLevel(level: Int): List<String> {
        return levelRewards[level] ?: emptyList()
    }

    data class PrefixData(val requiredLevel: Int, val display: String, val color: String, val broadcast: Boolean)
}
