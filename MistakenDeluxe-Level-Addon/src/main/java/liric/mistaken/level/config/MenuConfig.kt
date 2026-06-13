package liric.mistaken.level.config

import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class MenuConfig(private val plugin: LevelAddonPlugin) {

    lateinit var config: YamlConfiguration
        private set

    fun load() {
        val file = File(plugin.dataFolder, "menus.yml")
        if (!file.exists()) {
            plugin.saveResource("menus.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(file)
    }
}
