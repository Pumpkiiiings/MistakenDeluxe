package pumpking.lib.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException

class YamlConfigProvider(override val file: File) : ConfigProvider {
    private var config: FileConfiguration = YamlConfiguration()

    private fun loadUtf8(file: File): FileConfiguration {
        val yml = YamlConfiguration()
        if (file.exists()) {
            java.io.InputStreamReader(java.io.FileInputStream(file), Charsets.UTF_8).use { reader ->
                yml.load(reader)
            }
        }
        return yml
    }

    override fun load() {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        try {
            config = loadUtf8(file)
            
            // Auto-update missing keys from internal resources
            val plugin = pumpking.lib.core.PumpkingLib.plugin
            val relativePath = file.relativeToOrNull(plugin.dataFolder)?.path?.replace("\\", "/")
            
            val resourceStream = if (relativePath != null) plugin.getResource(relativePath) else plugin.getResource(file.name)
            if (resourceStream != null) {
                val defaultYml = YamlConfiguration()
                java.io.InputStreamReader(resourceStream, Charsets.UTF_8).use { defaultYml.load(it) }
                
                var changed = false
                for (key in defaultYml.getKeys(true)) {
                    if (!config.contains(key)) {
                        config.set(key, defaultYml.get(key))
                        changed = true
                    }
                }
                if (changed) {
                    save()
                }
            }
        } catch (e: Exception) {
            pumpking.lib.core.PumpkingLib.logError(
                pumpking.lib.core.PumpkingLib.LogCategory.CORE, 
                "Failed to load YAML file: ${file.name}. The file is corrupted. Renaming to .broken so it can be regenerated next boot. Error: ${e.message}"
            )
            file.renameTo(java.io.File(file.parentFile, file.name + ".broken"))
            config = YamlConfiguration() // fallback to empty
        }
    }

    override fun save() {
        try {
            config.save(file)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getString(path: String, def: String): String {
        return config.getString(path, def) ?: def
    }

    override fun getInt(path: String, def: Int): Int {
        return config.getInt(path, def)
    }

    override fun getBoolean(path: String, def: Boolean): Boolean {
        return config.getBoolean(path, def)
    }

    override fun getStringList(path: String): List<String> {
        return config.getStringList(path)
    }

    override fun contains(path: String): Boolean {
        return config.contains(path)
    }

    override fun set(path: String, value: Any?) {
        config.set(path, value)
    }

    override fun get(path: String): Any? {
        return config.get(path)
    }

    override fun getKeys(deep: Boolean): Set<String> {
        return config.getKeys(deep)
    }

    // Expose the raw configuration just in case
    override fun getRaw(): FileConfiguration = config
}
