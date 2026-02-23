package liric.mistaken.config

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * ConfigManager: El patrón de las configuraciones mecánicas.
 * Maneja los datos globales (cooldowns, items, precios) y los menús.
 */
class ConfigManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- ARCHIVOS GLOBALES (MECÁNICAS Y BALANCE) ---
    private lateinit var asesinosFile: File
    private lateinit var asesinosConfig: FileConfiguration

    private lateinit var supervivientesFile: File
    private lateinit var supervivientesConfig: FileConfiguration

    // Caché de menús (Thread-safe)
    private val menusCache = ConcurrentHashMap<String, FileConfiguration>()

    fun loadAllConfigs() {
        // Solo cargamos las mecánicas globales aquí.
        // Los idiomas se cargan en MessageConfig por separado.
        loadAsesinosConfig()
        loadSupervivientesConfig()
    }

    // --- ⚔️ GESTIÓN DE MECÁNICAS GLOBALES ---

    fun loadAsesinosConfig() {
        asesinosFile = File(plugin.dataFolder, "asesinos.yml")
        if (!asesinosFile.exists()) plugin.saveResource("asesinos.yml", false)
        asesinosConfig = YamlConfiguration.loadConfiguration(asesinosFile)
    }

    fun getAsesinos(): FileConfiguration = asesinosConfig

    fun loadSupervivientesConfig() {
        supervivientesFile = File(plugin.dataFolder, "supervivientes.yml")
        if (!supervivientesFile.exists()) plugin.saveResource("supervivientes.yml", false)
        supervivientesConfig = YamlConfiguration.loadConfiguration(supervivientesFile)
    }

    fun getSupervivientes(): FileConfiguration = supervivientesConfig

    // --- 📜 GESTIÓN DE MENÚS (CACHÉ REACTIVO) ---

    /**
     * Obtiene la configuración de un menú (ej: tienda_principal) de forma rápida.
     */
    fun getMenuConfig(fileName: String): FileConfiguration {
        return menusCache.getOrPut(fileName) {
            val menuFile = File(plugin.dataFolder, "menus/$fileName.yml")
            if (!menuFile.exists()) {
                plugin.saveResource("menus/$fileName.yml", false)
            }
            YamlConfiguration.loadConfiguration(menuFile)
        }
    }

    fun reloadMenus() {
        menusCache.clear()

        // Verificamos que el plugin ya haya arrancado completamente
        if (plugin.isReady) {
            plugin.shopSelector.reload()
            plugin.asesinoTienda.reload()
            plugin.supervivienteTienda.reload()
        }
        plugin.componentLogger.info(mm.deserialize("<gray>[Config] Caché de menús y UIs reiniciado.</gray>"))
    }

    // --- 🌍 PUENTES HACIA EL MOTOR DE IDIOMAS ---

    /**
     * Helper súper rápido para obtener el nombre traducido de un asesino.
     * Va y le pregunta al MessageConfig (asesinos_info.yml).
     */
    fun getAssassinName(player: Player?, assassinId: String): String {
        return plugin.messageConfig.getRawString(
            player = player,
            fileName = "asesinos_info",
            path = "asesinos.$assassinId.nombre",
            def = assassinId.uppercase()
        )
    }

    // --- 💾 PERSISTENCIA ASÍNCRONA ---

    fun saveAll() {
        saveConfigAsync(asesinosConfig, asesinosFile, "asesinos.yml")
        saveConfigAsync(supervivientesConfig, supervivientesFile, "supervivientes.yml")
    }

    private fun saveConfigAsync(config: FileConfiguration?, file: File, name: String) {
        if (config == null) return
        scope.launch {
            try {
                config.save(file)
            } catch (e: IOException) {
                plugin.componentLogger.error(mm.deserialize("<red>[System] Error al guardar $name: ${e.message}"))
            }
        }
    }

    // --- ⚡ UTILIDAD PARA CRAFTENGINE ---

    fun getCraftKey(path: String): String? {
        val value = asesinosConfig.getString("asesinos.$path") ?: return null
        if (value.isEmpty() || value.equals("none", ignoreCase = true)) return null

        return if (value.contains(":")) value
        else "${asesinosConfig.getString("namespace", "mistaken")}:$value"
    }
}
