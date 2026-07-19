package liric.mistaken.level.api

import liric.mistaken.level.LevelAddonPlugin
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

class MistakenLevelExpansion(private val plugin: LevelAddonPlugin) : PlaceholderExpansion() {

    override fun getIdentifier(): String {
        return "mistakenlevel"
    }

    override fun getAuthor(): String {
        return plugin.description.authors.joinToString(", ")
    }

    override fun getVersion(): String {
        return plugin.description.version
    }

    override fun persist(): Boolean {
        return true
    }

    override fun canRegister(): Boolean {
        return true
    }

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        if (player == null) return ""

        val level = plugin.manager.getLevel(player.uniqueId)
        val xp = plugin.manager.getExperience(player.uniqueId)
        val reqXp = plugin.manager.getRequiredXp(level)

        return when (params.lowercase()) {
            "level" -> level.toString()
            "xp" -> xp.toString()
            "xp_required" -> reqXp.toString()
            "progress" -> {
                val percent = if (reqXp > 0) (xp.toDouble() / reqXp.toDouble()) * 100.0 else 100.0
                String.format("%.1f", percent)
            }
            "level_prefix" -> plugin.levelConfig.getPrefixForLevel(level)?.display ?: ""
            "next_reward" -> {
                val nextLvl = plugin.levelConfig.config.getConfigurationSection("levels")?.getKeys(false)
                    ?.mapNotNull { it.toIntOrNull() }?.sorted()?.firstOrNull { it > level }
                
                if (nextLvl != null) {
                    val rewards = plugin.levelConfig.getRewardsForLevel(nextLvl)
                    if (rewards.isNotEmpty()) "Rewards at Level $nextLvl" else "Max Level Reached"
                } else {
                    "Max Level Reached"
                }
            }
            else -> null
        }
    }
}
