package liric.mistaken.api.managers

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player

interface IConfigManager {
    fun getKillerConfig(id: String): FileConfiguration
    fun getSurvivorConfig(id: String): FileConfiguration
    fun getMenuConfig(fileName: String): FileConfiguration
    fun getAssassinName(player: Player?, assassinId: String): String
}
