package liric.mistaken.level.rewards

import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.entity.Player

object RewardRegistry {
    private val executors = mutableMapOf<String, RewardExecutor>()

    /**
     * Registers a new reward executor type.
     * E.g. type = "message"
     */
    fun register(type: String, executor: RewardExecutor) {
        executors[type.lowercase()] = executor
    }

    /**
     * Parses and executes a reward string for a player.
     * Format: "[type] value"
     */
    fun executeReward(plugin: LevelAddonPlugin, player: Player, rewardString: String) {
        val trimmed = rewardString.trim()
        if (!trimmed.startsWith("[")) return

        val endBracket = trimmed.indexOf("]")
        if (endBracket == -1) return

        val type = trimmed.substring(1, endBracket).lowercase()
        val value = if (trimmed.length > endBracket + 1) trimmed.substring(endBracket + 1).trim() else ""

        val executor = executors[type]
        if (executor != null) {
            try {
                executor.execute(player, value)
            } catch (e: Exception) {
                plugin.logger.severe("Failed to execute reward type '$type' with value '$value' for ${player.name}: ${e.message}")
            }
        } else {
            plugin.logger.warning("Unknown reward type: $type in reward string '$rewardString'")
        }
    }
}
