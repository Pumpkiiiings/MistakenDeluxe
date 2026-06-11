package liric.mistaken.api.level

import java.util.UUID

/**
 * Service Provider Interface for Experience management.
 * 
 * Future addons should retrieve this via Bukkit's ServicesManager:
 * Bukkit.getServicesManager().load(ExperienceProvider::class.java)
 */
interface ExperienceProvider {
    /**
     * Gets the current experience of a player.
     */
    fun getExperience(uuid: UUID): Long

    /**
     * Adds experience to a player.
     * This may result in a level up if the threshold is reached.
     */
    fun addExperience(uuid: UUID, amount: Long)

    /**
     * Sets the exact experience of a player.
     * Use with caution.
     */
    fun setExperience(uuid: UUID, amount: Long)
    
    /**
     * Gets the total cumulative experience a player has earned over their lifetime.
     */
    fun getTotalExperience(uuid: UUID): Long
}
