package pumpking.lib.config

import kotlinx.coroutines.*
import liric.mistaken.api.managers.IConfigManager
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.Mistaken
import org.bukkit.configuration.file.FileConfiguration
import liric.mistaken.config.sync.CharacterMigrator
import pumpking.lib.config.sync.ConfigSynchronizer
import pumpking.lib.service.PumpkingServiceManager
import pumpking.lib.task.PumpkingTask

/**
 * [PUMPKING LIB]
 * ConfigManager: El patrón de las configuraciones mecánicas migrado a pumpking.lib.
 */
object ConfigManager : IConfigManager {


    private lateinit var plugin: JavaPlugin

    private val killersCache = ConcurrentHashMap<String, ConfigProvider>()
    private val survivorsCache = ConcurrentHashMap<String, ConfigProvider>()

    private val menusCache = ConcurrentHashMap<String, ConfigProvider>()
    // FIX #19: genericCache can grow indefinitely if callers use dynamic file names.
    // Current usage is bounded to a known set of config files, so growth is acceptable.
    // If this changes, replace with a Caffeine cache with maximumSize().
    private val genericCache = ConcurrentHashMap<String, ConfigProvider>()

    fun get(fileName: String): ConfigProvider {
        return genericCache.getOrPut(fileName) {
            val file = File(plugin.dataFolder, fileName)
            ConfigSynchronizer.sync(plugin, fileName, file)
            YamlConfigProvider(file).apply { load() }
        }
    }

    fun init(plugin: JavaPlugin) {
        this.plugin = plugin

        CharacterMigrator.migrate(plugin)
        loadAllConfigs()
        ConfigWatcher.init(plugin)
    }

    fun shutdown() {
        ConfigWatcher.shutdown()

        menusCache.clear()
    }

    fun loadAllConfigs() {
        val killersDir = File(plugin.dataFolder, "characters/killers")
        if (!killersDir.exists()) killersDir.mkdirs()
        killersDir.listFiles { _, name -> name.endsWith(".yml") }?.forEach { file ->
            val id = file.nameWithoutExtension.lowercase()
            killersCache[id] = YamlConfigProvider(file).apply { load() }
        }

        val survivorsDir = File(plugin.dataFolder, "characters/survivors")
        if (!survivorsDir.exists()) survivorsDir.mkdirs()
        survivorsDir.listFiles { _, name -> name.endsWith(".yml") }?.forEach { file ->
            val id = file.nameWithoutExtension.lowercase()
            survivorsCache[id] = YamlConfigProvider(file).apply { load() }
        }
    }

    override fun getKillerConfig(id: String): FileConfiguration {
        val provider = killersCache.getOrPut(id.lowercase()) {
            val file = File(plugin.dataFolder, "characters/killers/$id.yml")
            YamlConfigProvider(file).apply { load() }
        }
        return (provider as YamlConfigProvider).getRaw()
    }

    override fun getSurvivorConfig(id: String): FileConfiguration {
        val provider = survivorsCache.getOrPut(id.lowercase()) {
            val file = File(plugin.dataFolder, "characters/survivors/$id.yml")
            YamlConfigProvider(file).apply { load() }
        }
        return (provider as YamlConfigProvider).getRaw()
    }

    override fun getMenuConfig(fileName: String): FileConfiguration {
        val provider = menusCache.getOrPut(fileName) {
            val menuFile = File(plugin.dataFolder, "menus/$fileName.yml")
            ConfigSynchronizer.sync(plugin, "menus/$fileName.yml", menuFile)
            YamlConfigProvider(menuFile).apply { load() }
        }
        return (provider as YamlConfigProvider).getRaw()
    }

    fun reloadMenus() {
        menusCache.clear()
    }

    override fun getAssassinName(player: Player?, assassinId: String): String {
        // FIX #6: Removed the useless `val mistaken = plugin as Mistaken` cast.
        // The variable was never used after the cast, and having a library class depend on
        // a concrete plugin implementation violates layer separation (SRP/DIP).
        return PumpkingServiceManager.messages.getRawString(
            player = player,
            fileName = "killers_info",
            path = "asesinos.$assassinId.nombre",
            def = assassinId.uppercase()
        )
    }

    fun saveAll() {
        killersCache.forEach { (id, config) -> saveConfigAsync(config, "characters/killers/$id.yml") }
        survivorsCache.forEach { (id, config) -> saveConfigAsync(config, "characters/survivors/$id.yml") }
    }

    private fun saveConfigAsync(config: ConfigProvider, name: String) {
        PumpkingTask.ioScope.launch {
            try {
                config.save()
            } catch (e: Exception) {
                // Ignore exception, error logging handled by caller or core
            }
        }
    }

    fun getCraftKey(killerId: String, path: String): String? {
        val config = getKillerConfig(killerId)
        val value = config.getString(path)
        if (value == null || value.isEmpty() || value.equals("none", ignoreCase = true)) return null

        return if (value.contains(":")) value
        else "mistaken:$value"
    }
}


