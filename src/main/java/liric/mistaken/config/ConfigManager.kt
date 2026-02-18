package liric.mistaken.config

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * ConfigManager: Gestión de archivos YAML ultra-optimizada.
 * Implementa caché reactivo y guardado asíncrono para evitar micro-stutters.
 */
class ConfigManager(private val plugin: Mistaken) {

    private val mm = Mistaken.mm
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Archivos principales
    private lateinit var asesinosFile: File
    private lateinit var asesinosConfig: FileConfiguration

    private lateinit var supervivientesFile: File
    private lateinit var supervivientesConfig: FileConfiguration

    // Caché de menús concurrente (Thread-safe para reloads asíncronos)
    private val menusCache = ConcurrentHashMap<String, FileConfiguration>()

    fun loadAllConfigs() {
        loadAsesinosConfig()
        loadSupervivientesConfig()
    }

    /**
     * Gestión dinámica de menús.
     * Optimización: Solo carga el archivo si no está en memoria.
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

    /**
     * Limpia el caché de menús para permitir cambios en caliente.
     */
    fun reloadMenus() {
        menusCache.clear()
        logSuccess("Caché de menús reiniciado")
    }

    // --- ASESINOS ---
    fun loadAsesinosConfig() {
        asesinosFile = File(plugin.dataFolder, "asesinos.yml")
        if (!asesinosFile.exists()) plugin.saveResource("asesinos.yml", false)
        asesinosConfig = YamlConfiguration.loadConfiguration(asesinosFile)
        logSuccess("Configuración de asesinos")
    }

    fun getAsesinos(): FileConfiguration = asesinosConfig

    // --- SUPERVIVIENTES ---
    fun loadSupervivientesConfig() {
        supervivientesFile = File(plugin.dataFolder, "supervivientes.yml")
        if (!supervivientesFile.exists()) plugin.saveResource("supervivientes.yml", false)
        supervivientesConfig = YamlConfiguration.loadConfiguration(supervivientesFile)
        logSuccess("Configuración de supervivientes")
    }

    fun getSupervivientes(): FileConfiguration = supervivientesConfig

    /**
     * Guarda todas las configuraciones de forma asíncrona.
     */
    fun saveAll() {
        saveConfigAsync(asesinosConfig, asesinosFile, "asesinos.yml")
        saveConfigAsync(supervivientesConfig, supervivientesFile, "supervivientes.yml")
    }

    /**
     * Motor de guardado asíncrono en hilo IO.
     */
    private fun saveConfigAsync(config: FileConfiguration?, file: File, name: String) {
        if (config == null) return
        scope.launch {
            try {
                config.save(file)
            } catch (e: IOException) {
                logError(name, e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Utilidad para obtener NamespacedKeys de texturas/items (e.g. Oraxen/ItemsAdder/Nativo).
     */
    fun getCraftKey(path: String): String? {
        val value = asesinosConfig.getString("asesinos.$path") ?: return null
        if (value.isEmpty() || value.equals("none", ignoreCase = true)) return null

        return if (value.contains(":")) value
        else "${asesinosConfig.getString("namespace", "mistaken")}:$value"
    }

    private fun logSuccess(system: String) {
        plugin.server.consoleSender.sendMessage(mm.deserialize(
            "<gray>[Mistaken]</gray> <green>$system cargada correctamente.</green>"
        ))
    }

    private fun logError(file: String, error: String) {
        plugin.server.consoleSender.sendMessage(mm.deserialize(
            "<red>[Mistaken] Error crítico al guardar $file: $error</red>"
        ))
    }
}
