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
 * FIX: Thread-Safe Saving y Carga Asíncrona para evitar lag en reloads.
 */
class ConfigManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Archivos
    private val asesinosFile by lazy { File(plugin.dataFolder, "asesinos.yml") }
    private val supervivientesFile by lazy { File(plugin.dataFolder, "supervivientes.yml") }

    // Configuraciones en RAM (Volatile para visibilidad entre hilos)
    @Volatile private lateinit var asesinosConfig: FileConfiguration
    @Volatile private lateinit var supervivientesConfig: FileConfiguration

    // Caché de menús (Thread-safe)
    private val menusCache = ConcurrentHashMap<String, FileConfiguration>()

    // Candados para escritura segura
    private val asesinosLock = Any()
    private val supervivientesLock = Any()

    fun loadAllConfigs() {
        // Carga inicial (puede ser síncrona al inicio del server, pero segura)
        if (!asesinosFile.exists()) plugin.saveResource("asesinos.yml", false)
        asesinosConfig = YamlConfiguration.loadConfiguration(asesinosFile)

        if (!supervivientesFile.exists()) plugin.saveResource("supervivientes.yml", false)
        supervivientesConfig = YamlConfiguration.loadConfiguration(supervivientesFile)
    }

    // --- ⚔️ GETTERS (LECTURA RÁPIDA DESDE RAM) ---

    fun getAsesinos(): FileConfiguration = asesinosConfig
    fun getSupervivientes(): FileConfiguration = supervivientesConfig

    // --- 📜 GESTIÓN DE MENÚS (CACHÉ REACTIVO) ---

    fun getMenuConfig(fileName: String): FileConfiguration {
        return menusCache.getOrPut(fileName) {
            val menuFile = File(plugin.dataFolder, "menus/$fileName.yml")
            if (!menuFile.exists()) {
                // Si no existe, intentamos sacarlo del JAR o crear uno vacío
                if (plugin.getResource("menus/$fileName.yml") != null) {
                    plugin.saveResource("menus/$fileName.yml", false)
                }
            }
            YamlConfiguration.loadConfiguration(menuFile)
        }
    }

    fun reloadMenus() {
        menusCache.clear()

        // Recarga de UIs en el hilo principal (necesario para GUIs)
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (plugin.isReady) {
                // Asumiendo que tus clases de tienda tienen método reload()
                // plugin.shopSelector.reload()
                // plugin.asesinoTienda.reload()
            }
        })
        plugin.componentLogger.info(mm.deserialize("<gray>[Config] Caché de menús reiniciado.</gray>"))
    }

    // --- 🌍 PUENTES HACIA EL MOTOR DE IDIOMAS ---

    fun getAssassinName(player: Player?, assassinId: String): String {
        return plugin.messageConfig.getRawString(
            player = player,
            fileName = "asesinos_info",
            path = "asesinos.$assassinId.nombre",
            def = assassinId.uppercase()
        )
    }

    // --- 💾 PERSISTENCIA ASÍNCRONA SEGURA ---

    fun saveAll() {
        saveConfigAsync(asesinosConfig, asesinosFile, asesinosLock, "asesinos.yml")
        saveConfigAsync(supervivientesConfig, supervivientesFile, supervivientesLock, "supervivientes.yml")
    }

    private fun saveConfigAsync(config: FileConfiguration, file: File, lock: Any, name: String) {
        scope.launch {
            try {
                // 🔥 FIX: synchronized evita corrupción de archivos
                synchronized(lock) {
                    config.save(file)
                }
            } catch (e: IOException) {
                plugin.componentLogger.error(mm.deserialize("<red>[System] Error crítico guardando $name: ${e.message}</red>"))
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
