package pumpking.lib.config

import kotlinx.coroutines.*
import liric.mistaken.api.managers.IConfigManager
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * [PUMPKING LIB]
 * ConfigManager: El patrón de las configuraciones mecánicas migrado a pumpking.lib.
 */
object ConfigManager : IConfigManager {


    private lateinit var plugin: JavaPlugin

    @Volatile private lateinit var asesinosConfig: ConfigProvider
    @Volatile private lateinit var supervivientesConfig: ConfigProvider

    private val menusCache = ConcurrentHashMap<String, ConfigProvider>()
    private val genericCache = ConcurrentHashMap<String, ConfigProvider>()

    fun get(fileName: String): ConfigProvider {
        return genericCache.getOrPut(fileName) {
            val file = File(plugin.dataFolder, fileName)
            pumpking.lib.config.sync.ConfigSynchronizer.sync(plugin, fileName, file)
            YamlConfigProvider(file).apply { load() }
        }
    }

    fun init(plugin: JavaPlugin) {
        this.plugin = plugin

        loadAllConfigs()
        ConfigWatcher.init(plugin)
    }

    fun shutdown() {
        ConfigWatcher.shutdown()

        menusCache.clear()
    }

    fun loadAllConfigs() {
        val asesinosFile = File(plugin.dataFolder, "asesinos.yml")
        pumpking.lib.config.sync.ConfigSynchronizer.sync(plugin, "asesinos.yml", asesinosFile)
        asesinosConfig = YamlConfigProvider(asesinosFile).apply { load() }

        val supervivientesFile = File(plugin.dataFolder, "supervivientes.yml")
        pumpking.lib.config.sync.ConfigSynchronizer.sync(plugin, "supervivientes.yml", supervivientesFile)
        supervivientesConfig = YamlConfigProvider(supervivientesFile).apply { load() }
    }

    override fun getAsesinos(): org.bukkit.configuration.file.FileConfiguration = (asesinosConfig as YamlConfigProvider).getRaw()
    override fun getSupervivientes(): org.bukkit.configuration.file.FileConfiguration = (supervivientesConfig as YamlConfigProvider).getRaw()

    override fun getMenuConfig(fileName: String): org.bukkit.configuration.file.FileConfiguration {
        val provider = menusCache.getOrPut(fileName) {
            val menuFile = File(plugin.dataFolder, "menus/$fileName.yml")
            pumpking.lib.config.sync.ConfigSynchronizer.sync(plugin, "menus/$fileName.yml", menuFile)
            YamlConfigProvider(menuFile).apply { load() }
        }
        return (provider as YamlConfigProvider).getRaw()
    }

    fun reloadMenus() {
        menusCache.clear()
    }

    override fun getAssassinName(player: Player?, assassinId: String): String {

        val mistaken = plugin as liric.mistaken.Mistaken
        return pumpking.lib.service.PumpkingServiceManager.messages.getRawString(
            player = player,
            fileName = "asesinos_info",
            path = "asesinos.$assassinId.nombre",
            def = assassinId.uppercase()
        )
    }

    fun saveAll() {
        saveConfigAsync(asesinosConfig, "asesinos.yml")
        saveConfigAsync(supervivientesConfig, "supervivientes.yml")
    }

    private fun saveConfigAsync(config: ConfigProvider, name: String) {
        pumpking.lib.task.PumpkingTask.ioScope.launch {
            try {
                config.save()
            } catch (e: Exception) {
                // Ignore exception, error logging handled by caller or core
            }
        }
    }

    fun getCraftKey(path: String): String? {
        val value = asesinosConfig.getString("asesinos.$path")
        if (value.isEmpty() || value.equals("none", ignoreCase = true)) return null

        return if (value.contains(":")) value
        else "${asesinosConfig.getString("namespace", "mistaken")}:$value"
    }
}


