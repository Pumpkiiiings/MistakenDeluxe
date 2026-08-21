package liric.mistaken.config.engine.sync

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import liric.mistaken.config.engine.core.ConfigProvider
import liric.mistaken.config.engine.core.YamlConfigProvider
import liric.mistaken.MistakenLib
import java.io.File
import java.io.InputStreamReader
import org.bukkit.configuration.ConfigurationSection

object ConfigSynchronizer {

    fun sync(plugin: JavaPlugin, fileName: String, localFile: File): ConfigMigrationResult {
        
        if (!fileName.endsWith(".yml")) {
            return ConfigMigrationResult(fileName, 0, 0, 0, false)
        }

        
        if (!localFile.exists()) {
            localFile.parentFile.mkdirs()
            plugin.saveResource(fileName, false)
            MistakenLib.log(MistakenLib.LogCategory.CONFIG, "Created default configuration: $fileName")
            return ConfigMigrationResult(fileName, 0, 0, 0, false)
        }

        
        val localConfig = YamlConfigProvider(localFile).apply { load() }

        
        val resourceStream = plugin.getResource(fileName) ?: return ConfigMigrationResult(fileName, 0, 0, 0, false)
        val defaultReader = InputStreamReader(resourceStream, Charsets.UTF_8)
        val defaultConfig = YamlConfiguration.loadConfiguration(defaultReader)

        var isUpdated = false
        var pathsAdded = 0
        val migratedFrom = localConfig.getInt("config-version", 1)
        val targetVersion = defaultConfig.getInt("config-version", 1)

        
        val defaultKeys = defaultConfig.getKeys(true)
        for (key in defaultKeys) {
            
            
            

            if (!localConfig.contains(key)) {
                val defaultValue = defaultConfig.get(key)
                
                
                
                

                if (defaultValue !is ConfigurationSection) {
                    localConfig.set(key, defaultValue)
                    pathsAdded++
                    isUpdated = true
                    MistakenLib.log(MistakenLib.LogCategory.CONFIG, "Added missing path: $key")
                }
            }
        }

        
        if (isUpdated) {
            localConfig.save()
            MistakenLib.log(MistakenLib.LogCategory.CONFIG, "Synchronization completed successfully for $fileName")
        }

        return ConfigMigrationResult(fileName, pathsAdded, migratedFrom, targetVersion, isUpdated)
    }
}
