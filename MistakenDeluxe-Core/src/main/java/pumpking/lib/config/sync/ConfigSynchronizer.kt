package pumpking.lib.config.sync

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import pumpking.lib.config.ConfigProvider
import pumpking.lib.config.YamlConfigProvider
import pumpking.lib.core.PumpkingLib
import java.io.File
import java.io.InputStreamReader

object ConfigSynchronizer {

    fun sync(plugin: JavaPlugin, fileName: String, localFile: File): ConfigMigrationResult {
        // Only YAML supported for now via Bukkit's YamlConfiguration for stream reading
        if (!fileName.endsWith(".yml")) {
            return ConfigMigrationResult(fileName, 0, 0, 0, false)
        }

        // If it doesn't exist, just save default and return
        if (!localFile.exists()) {
            localFile.parentFile.mkdirs()
            plugin.saveResource(fileName, false)
            PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "Created default configuration: $fileName")
            return ConfigMigrationResult(fileName, 0, 0, 0, false)
        }

        // Load local file
        val localConfig = YamlConfigProvider(localFile)

        // Load default from JAR
        val resourceStream = plugin.getResource(fileName) ?: return ConfigMigrationResult(fileName, 0, 0, 0, false)
        val defaultReader = InputStreamReader(resourceStream, Charsets.UTF_8)
        val defaultConfig = YamlConfiguration.loadConfiguration(defaultReader)

        var isUpdated = false
        var pathsAdded = 0
        var migratedFrom = localConfig.getInt("config-version", 1)
        val targetVersion = defaultConfig.getInt("config-version", 1)

        // 1. Run migrations if necessary
        if (migratedFrom < targetVersion) {
            val migrated = ConfigVersionManager.migrate(fileName, localConfig, migratedFrom, targetVersion)
            if (migrated) {
                isUpdated = true
                migratedFrom = targetVersion
            }
        }

        // 2. Scan and inject missing paths
        val defaultKeys = defaultConfig.getKeys(true)
        for (key in defaultKeys) {
            // Ignore if it's just a section parent without actual value if Bukkit returns them
            // Bukkit YamlConfiguration returns section paths as keys.
            // We only want to set missing values, but a missing section will be implicitly created.

            if (!localConfig.contains(key)) {
                val defaultValue = defaultConfig.get(key)
                // If it's a ConfigurationSection, we don't necessarily set it directly,
                // but setting a section in Bukkit copies its children, which is fine,
                // but Bukkit's getKeys(true) will also list the children.
                // To avoid duplicate logs for parent and children, we can just set them all.

                if (defaultValue !is org.bukkit.configuration.ConfigurationSection) {
                    localConfig.set(key, defaultValue)
                    pathsAdded++
                    isUpdated = true
                    PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "Added missing path: $key")
                }
            }
        }

        // 3. Save if updated
        if (isUpdated) {
            localConfig.save()
            PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "Synchronization completed successfully for $fileName")
        }

        return ConfigMigrationResult(fileName, pathsAdded, migratedFrom, targetVersion, isUpdated)
    }
}
