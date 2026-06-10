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

    private lateinit var scope: CoroutineScope
    private lateinit var plugin: JavaPlugin

    @Volatile private lateinit var asesinosConfig: ConfigProvider
    @Volatile private lateinit var supervivientesConfig: ConfigProvider

    private val menusCache = ConcurrentHashMap<String, ConfigProvider>()

    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
        this.scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        loadAllConfigs()
        ConfigWatcher.init(plugin)
    }

    fun shutdown() {
        ConfigWatcher.shutdown()
        if (::scope.isInitialized) {
            scope.cancel()
        }
        menusCache.clear()
    }

    fun loadAllConfigs() {
        val asesinosFile = File(plugin.dataFolder, "asesinos.yml")
        pumpking.lib.config.sync.ConfigSynchronizer.sync(plugin, "asesinos.yml", asesinosFile)
        asesinosConfig = YamlConfigProvider(asesinosFile)

        val supervivientesFile = File(plugin.dataFolder, "supervivientes.yml")
        pumpking.lib.config.sync.ConfigSynchronizer.sync(plugin, "supervivientes.yml", supervivientesFile)
        supervivientesConfig = YamlConfigProvider(supervivientesFile)
    }

    override fun getAsesinos(): org.bukkit.configuration.file.FileConfiguration = (asesinosConfig as YamlConfigProvider).getRaw()
    override fun getSupervivientes(): org.bukkit.configuration.file.FileConfiguration = (supervivientesConfig as YamlConfigProvider).getRaw()

    override fun getMenuConfig(fileName: String): org.bukkit.configuration.file.FileConfiguration {
        val provider = menusCache.getOrPut(fileName) {
            val menuFile = File(plugin.dataFolder, "menus/$fileName.yml")
            pumpking.lib.config.sync.ConfigSynchronizer.sync(plugin, "menus/$fileName.yml", menuFile)
            YamlConfigProvider(menuFile)
        }
        return (provider as YamlConfigProvider).getRaw()
    }

    fun reloadMenus() {
        menusCache.clear()
    }

    override fun getAssassinName(player: Player?, assassinId: String): String {
        // Fallback for getting name since messageConfig isn't explicitly defined here anymore
        // Actually, since we need messageConfig, we might need to cast plugin to Mistaken
        val mistaken = plugin as liric.mistaken.Mistaken
        return mistaken.messageConfig.getRawString(
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
        scope.launch {
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

