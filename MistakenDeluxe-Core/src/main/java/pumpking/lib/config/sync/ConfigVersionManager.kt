package pumpking.lib.config.sync

import pumpking.lib.config.ConfigProvider
import pumpking.lib.core.PumpkingLib

object ConfigVersionManager {

    // fileName -> list of migrations
    private val migrations = mutableMapOf<String, MutableList<ConfigMigration>>()

    fun registerMigration(
        fileName: String,
        fromVersion: Int,
        toVersion: Int,
        action: ConfigMigrationAction.() -> Unit
    ) {
        val list = migrations.getOrPut(fileName) { mutableListOf() }
        list.add(ConfigMigration(fromVersion, toVersion, action))
    }

    fun migrate(fileName: String, config: ConfigProvider, currentVersion: Int, targetVersion: Int): Boolean {
        if (currentVersion >= targetVersion) return false

        val fileMigrations = migrations[fileName] ?: return false
        
        var version = currentVersion
        var wasMigrated = false
        
        // Sort migrations by fromVersion just in case
        val sortedMigrations = fileMigrations.sortedBy { it.fromVersion }

        for (migration in sortedMigrations) {
            if (migration.fromVersion == version && migration.toVersion <= targetVersion) {
                PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "Migrating config $fileName version ${migration.fromVersion} -> ${migration.toVersion}")
                
                val actionContext = ConfigMigrationAction(config)
                migration.action.invoke(actionContext)
                
                config.set("config-version", migration.toVersion)
                version = migration.toVersion
                wasMigrated = true
                
                PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "Migration to version $version completed.")
            }
        }
        
        return wasMigrated
    }
}

class ConfigMigrationAction(private val config: ConfigProvider) {
    
    fun renamePath(oldPath: String, newPath: String) {
        if (config.contains(oldPath)) {
            val value = config.get(oldPath)
            config.set(newPath, value)
            config.set(oldPath, null)
            PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "Renamed path: $oldPath -> $newPath")
        }
    }

    fun removePath(path: String) {
        if (config.contains(path)) {
            config.set(path, null)
            PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "Removed path: $path")
        }
    }

    fun setDefaultIfMissing(path: String, value: Any?) {
        if (!config.contains(path)) {
            config.set(path, value)
            PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "Set default value for path: $path")
        }
    }
}

data class ConfigMigration(
    val fromVersion: Int,
    val toVersion: Int,
    val action: ConfigMigrationAction.() -> Unit
)
