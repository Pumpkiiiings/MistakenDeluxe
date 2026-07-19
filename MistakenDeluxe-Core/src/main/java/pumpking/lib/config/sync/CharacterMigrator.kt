package pumpking.lib.config.sync

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object CharacterMigrator {
    fun migrate(plugin: JavaPlugin) {
        val asesinosFile = File(plugin.dataFolder, "asesinos.yml")
        val supervivientesFile = File(plugin.dataFolder, "supervivientes.yml")

        if (asesinosFile.exists()) {
            val config = YamlConfiguration.loadConfiguration(asesinosFile)
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
            
            // Renombrar a .old para evitar re-migración
            asesinosFile.renameTo(File(plugin.dataFolder, "asesinos.yml.old"))
            plugin.logger.info("Migrados ${keys.size} asesinos a archivos individuales.")
        }

        if (supervivientesFile.exists()) {
            val config = YamlConfiguration.loadConfiguration(supervivientesFile)
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
            
            // Renombrar a .old para evitar re-migración
            supervivientesFile.renameTo(File(plugin.dataFolder, "supervivientes.yml.old"))
            plugin.logger.info("Migrados ${keys.size} supervivientes a archivos individuales.")
        }
    }
}
