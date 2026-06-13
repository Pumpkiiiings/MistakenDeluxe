package liric.mistaken.level.config

import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class XpSourcesConfig(private val plugin: LevelAddonPlugin) {

    lateinit var config: YamlConfiguration
        private set

    private val sources = mutableMapOf<String, Long>()

    fun load() {
        val file = File(plugin.dataFolder, "xp_sources.yml")
        if (!file.exists()) {
            plugin.saveResource("xp_sources.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(file)

        sources.clear()
        val sourcesSection = config.getConfigurationSection("sources")
        if (sourcesSection != null) {
            for (key in sourcesSection.getKeys(false)) {
                sources[key.lowercase()] = sourcesSection.getLong(key)
            }
        }
    }

    fun getXpForSource(source: String): Long {
        return sources[source.lowercase()] ?: 0L
    }
}
