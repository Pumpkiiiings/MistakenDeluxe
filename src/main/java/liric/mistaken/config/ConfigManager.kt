package liric.mistaken.config

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.IOException

/**
 * [LIRIC-MISTAKEN 2.0]
 * ConfigManager: Centralizador de acceso a datos locales y globales.
 * Actúa como puente entre el núcleo del plugin y el motor de traducciones.
 */
class ConfigManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- 📂 ACCESO A CONFIGURACIONES GLOBALES (Raíz) ---

    /**
     * Obtiene el archivo config.yml principal del plugin.
     */
    val config: FileConfiguration
        get() = plugin.config

    // --- 🛠️ MÉTODOS DE RECARGA ---

    /**
     * 🔥 FIX: Método principal de carga.
     * Se llama desde el onEnable del plugin principal.
     */
    fun loadAllConfigs() {
        // En el sistema de carpetas, esto dispara el escaneo total del JAR y carpetas
        plugin.messageConfig.loadAllLanguages()

        plugin.componentLogger.info(mm.deserialize("<gray>[Config] Sistema de configuraciones y lenguajes sincronizado.</gray>"))
    }

    /**
     * Recarga la base de datos de asesinos refrescando el motor de idiomas.
     */
    fun loadAsesinosConfig() {
        plugin.messageConfig.loadAllLanguages()
    }

    /**
     * Recarga la base de datos de supervivientes.
     */
    fun loadSupervivientesConfig() {
        plugin.messageConfig.loadAllLanguages()
    }

    /**
     * Limpia el caché de los menús y los obliga a re-procesar los archivos YAML.
     */
    fun reloadMenus() {
        // Usamos el semáforo global para evitar UninitializedPropertyAccessException
        if (!plugin.isReady) return

        // Aquí ya es seguro llamarlos porque isReady = true garantiza que ya existen
        plugin.shopSelector.reload()
        plugin.asesinoTienda.reload()
        plugin.supervivienteTienda.reload()
    }

    // --- 🌍 PUENTE HACIA EL MOTOR DE IDIOMAS (I18n) ---

    /**
     * Obtiene la configuración de un menú adaptada al idioma del jugador.
     */
    fun getMenuConfig(player: Player, menuName: String): FileConfiguration {
        return plugin.messageConfig.getSpecificFile(player, menuName)
    }

    /**
     * Obtiene el archivo asesinos.yml del idioma del jugador (Lores, Nombres).
     */
    fun getAsesinosConfig(player: Player?): FileConfiguration {
        return plugin.messageConfig.getSpecificFile(player, "asesinos")
    }

    /**
     * Obtiene el archivo supervivientes.yml del idioma del jugador.
     */
    fun getSupervivientesConfig(player: Player?): FileConfiguration {
        return plugin.messageConfig.getSpecificFile(player, "supervivientes")
    }

    /**
     * Helper para obtener el nombre traducido de un asesino al vuelo.
     */
    fun getAssassinName(player: Player, assassinId: String): String {
        val config = getAsesinosConfig(player)
        return config.getString("asesinos.$assassinId.nombre") ?: assassinId
    }

    // --- 💾 PERSISTENCIA ASÍNCRONA ---

    /**
     * Guarda cambios en un archivo de idioma específico de forma asíncrona.
     */
    fun saveLangFileAsync(player: Player, fileName: String, fileConfig: FileConfiguration) {
        val lang = player.let { plugin.playerDataManager.getLanguage(it.uniqueId) } ?: "es"
        val file = File(plugin.dataFolder, "lang/$lang/$fileName.yml")

        scope.launch {
            try {
                fileConfig.save(file)
            } catch (e: IOException) {
                plugin.componentLogger.error(mm.deserialize("<red>[System] Error al guardar $lang/$fileName: ${e.message}"))
            }
        }
    }

    // --- ⚡ UTILIDAD PARA CRAFTENGINE ---

    /**
     * Resuelve la NamespacedKey de un item custom basado en la config del idioma.
     */
    fun getCraftKey(player: Player, path: String): String? {
        val config = getAsesinosConfig(player)
        val value = config.getString("asesinos.$path") ?: return null

        if (value.isEmpty() || value.equals("none", ignoreCase = true)) return null

        return if (value.contains(":")) value
        else {
            val defaultNamespace = config.getString("namespace", "mistaken")
            "$defaultNamespace:$value"
        }
    }
}
