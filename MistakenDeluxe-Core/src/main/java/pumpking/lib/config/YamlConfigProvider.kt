package pumpking.lib.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException

class YamlConfigProvider(override val file: File) : ConfigProvider {
    private var config: FileConfiguration = YamlConfiguration.loadConfiguration(file)

    override fun load() {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        config = YamlConfiguration.loadConfiguration(file)
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
    fun getRaw(): FileConfiguration = config
}
