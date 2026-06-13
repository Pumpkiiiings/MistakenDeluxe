package liric.mistaken.api.requirements

import org.bukkit.entity.Player

object RequirementEngine {
    private val providers = mutableListOf<RequirementProvider>()

    fun registerProvider(provider: RequirementProvider) {
        providers.add(provider)
    }

    /**
     * Checks if the player meets all registered requirements for the specified category and id.
     */
    fun meetsAllRequirements(player: Player, category: String, id: String): Boolean {
        return providers.all { it.meetsRequirement(player, category, id) }
    }

    /**
     * Returns a list of message strings explaining why the player does not meet the requirements.
     * Returns an empty list if all requirements are met.
     */
    fun getRequirementMessages(player: Player, category: String, id: String): List<String> {
        val messages = mutableListOf<String>()
        for (provider in providers) {
            if (!provider.meetsRequirement(player, category, id)) {
                val msg = provider.getRequirementMessage(player, category, id)
                if (msg != null) {
                    messages.add(msg)
                }
            }
        }
        return messages
    }
}
