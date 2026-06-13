package liric.mistaken.api.requirements

import org.bukkit.entity.Player

interface RequirementProvider {
    /**
     * Checks if the player meets the requirement for the given category and id.
     * E.g. category = "killers", id = "slasher"
     */
    fun meetsRequirement(player: Player, category: String, id: String): Boolean

    /**
     * Optional message or lore component text (MiniMessage format) explaining what is required.
     * E.g. "<red>Required Level: 10"
     */
    fun getRequirementMessage(player: Player, category: String, id: String): String?
}
