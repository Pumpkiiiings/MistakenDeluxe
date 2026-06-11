package liric.mistaken.api.level

import java.util.UUID

/**
 * Service Provider Interface for Level management.
 * 
 * Future addons should retrieve this via Bukkit's ServicesManager:
 * Bukkit.getServicesManager().load(LevelProvider::class.java)
 */
interface LevelProvider {
    /**
     * Gets the current level of a player.
     */
    fun getLevel(uuid: UUID): Int

    /**
     * Sets the level of a player, overriding their current level.
     * This may trigger a LevelUp event if the new level is higher.
     */
    fun setLevel(uuid: UUID, level: Int)

    /**
     * Adds the specified number of levels to a player.
     */
    fun addLevel(uuid: UUID, amount: Int)
}
