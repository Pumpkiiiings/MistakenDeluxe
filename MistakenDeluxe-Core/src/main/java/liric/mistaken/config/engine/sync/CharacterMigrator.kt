package liric.mistaken.config.engine.sync

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object CharacterMigrator {
    fun migrate(plugin: JavaPlugin) {
        val killersFile = File(plugin.dataFolder, "killers.yml")
        val survivorsFile = File(plugin.dataFolder, "survivors.yml")

        if (killersFile.exists()) {
            val config = YamlConfiguration.loadConfiguration(killersFile)
            val keys = config.getConfigurationSection("asesinos")?.getKeys(false) ?: emptySet()
            
            val killersDir = File(plugin.dataFolder, "characters/killers")
            if (!killersDir.exists()) killersDir.mkdirs()

            keys.forEach { id ->
                val section = config.getConfigurationSection("killers.$id")
                if (section != null) {
                    val individualFile = File(killersDir, "$id.yml")
                    val individualConfig = YamlConfiguration.loadConfiguration(individualFile)
                    
                    section.getKeys(true).forEach { key ->
                        individualConfig.set(key, section.get(key))
                    }
                    individualConfig.set("id", id) 
                    individualConfig.save(individualFile)
                }
            }
            
            
            killersFile.renameTo(File(plugin.dataFolder, "killers.yml.old"))
            plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Migrated ${keys.size} killers to individual files.</gray>"))
        }

        if (survivorsFile.exists()) {
            val config = YamlConfiguration.loadConfiguration(survivorsFile)
            val keys = config.getConfigurationSection("supervivientes")?.getKeys(false) ?: emptySet()
            
            val survivorsDir = File(plugin.dataFolder, "characters/survivors")
            if (!survivorsDir.exists()) survivorsDir.mkdirs()

            keys.forEach { id ->
                val section = config.getConfigurationSection("survivors.$id")
                if (section != null) {
                    val individualFile = File(survivorsDir, "$id.yml")
                    val individualConfig = YamlConfiguration.loadConfiguration(individualFile)
                    
                    section.getKeys(true).forEach { key ->
                        individualConfig.set(key, section.get(key))
                    }
                    individualConfig.set("id", id) 
                    individualConfig.save(individualFile)
                }
            }
            
            
            survivorsFile.renameTo(File(plugin.dataFolder, "survivors.yml.old"))
            plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Migrated ${keys.size} survivors to individual files.</gray>"))
        }
    }
}
