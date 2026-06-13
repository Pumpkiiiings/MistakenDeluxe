package liric.mistaken.level.rewards

import org.bukkit.entity.Player

interface RewardExecutor {
    /**
     * Executes the reward for the player.
     * @param player The player to reward.
     * @param value The value associated with the reward (e.g., text, id, or number).
     */
    fun execute(player: Player, value: String)
}
