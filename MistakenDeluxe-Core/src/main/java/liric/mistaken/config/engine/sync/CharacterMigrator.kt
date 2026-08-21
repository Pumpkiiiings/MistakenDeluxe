package liric.mistaken.config.engine.sync

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object CharacterMigrator {
    fun migrate(plugin: JavaPlugin) {
        val killersFile = File(plugin.dataFolder, "asesinos.yml")
        val survivorsFile = File(plugin.dataFolder, "supervivientes.yml")

        if (killersFile.exists()) {
            val config = YamlConfiguration.loadConfiguration(killersFile)
            val keys = config.getConfigurationSection("asesinos")?.getKeys(false) ?: emptySet()
            
            val killersDir = File(plugin.dataFolder, "characters/killers")
            if (!killersDir.exists()) killersDir.mkdirs()

            keys.forEach { id ->
                val section = config.getConfigurationSection("asesinos.$id")
                if (section != null) {
                    val individualFile = File(killersDir, "$id.yml")
                    val individualConfig = YamlConfiguration.loadConfiguration(individualFile)
                    // Volcar datos
                    section.getKeys(true).forEach { key ->
                        individualConfig.set(key, section.get(key))
                    }
                    individualConfig.set("id", id) // Garantizar ID
                    individualConfig.save(individualFile)
                }
            }
            
            // Renombrar a .old para evitar re-migraci�n
            killersFile.renameTo(File(plugin.dataFolder, "asesinos.yml.old"))
            plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Migrated ${keys.size} killers to individual files.</gray>"))
        }

        if (survivorsFile.exists()) {
            val config = YamlConfiguration.loadConfiguration(survivorsFile)
            val keys = config.getConfigurationSection("supervivientes")?.getKeys(false) ?: emptySet()
            
            val survivorsDir = File(plugin.dataFolder, "characters/survivors")
            if (!survivorsDir.exists()) survivorsDir.mkdirs()

            keys.forEach { id ->
                val section = config.getConfigurationSection("supervivientes.$id")
                if (section != null) {
                    val individualFile = File(survivorsDir, "$id.yml")
                    val individualConfig = YamlConfiguration.loadConfiguration(individualFile)
                    // Volcar datos
                    section.getKeys(true).forEach { key ->
                        individualConfig.set(key, section.get(key))
                    }
                    individualConfig.set("id", id) // Garantizar ID
                    individualConfig.save(individualFile)
                }
            }
            
            // Renombrar a .old para evitar re-migraci�n
            survivorsFile.renameTo(File(plugin.dataFolder, "supervivientes.yml.old"))
            plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Migrated ${keys.size} survivors to individual files.</gray>"))
        }
    }
}
