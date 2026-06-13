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
    private val levelRequirements = mutableMapOf<Int, LevelRequirements>()

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

        // Load Levels/Rewards/Requirements
        levelRewards.clear()
        levelRequirements.clear()
        val levelsSection = config.getConfigurationSection("levels")
        if (levelsSection != null) {
            for (key in levelsSection.getKeys(false)) {
                val level = key.toIntOrNull() ?: continue
                val rewards = levelsSection.getStringList("$key.rewards")
                levelRewards[level] = rewards

                val reqSection = levelsSection.getConfigurationSection("$key.requirements")
                val xp = reqSection?.getLong("xp") ?: 0L
                val kills = reqSection?.getInt("kills") ?: 0
                val winsS = reqSection?.getInt("wins_survivor") ?: 0
                val winsA = reqSection?.getInt("wins_killer") ?: 0
                val winsG = reqSection?.getInt("wins_global") ?: 0
                val gens = reqSection?.getInt("generators_repair") ?: 0

                levelRequirements[level] = LevelRequirements(xp, kills, winsS, winsA, winsG, gens)
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

    fun getRequirementsForLevel(level: Int): LevelRequirements? {
        return levelRequirements[level]
    }

    data class PrefixData(val requiredLevel: Int, val display: String, val color: String, val broadcast: Boolean)

    data class LevelRequirements(
        val xp: Long,
        val kills: Int,
        val winsSurvivor: Int,
        val winsAssassin: Int,
        val winsGlobal: Int,
        val generatorsRepaired: Int
    )
}
