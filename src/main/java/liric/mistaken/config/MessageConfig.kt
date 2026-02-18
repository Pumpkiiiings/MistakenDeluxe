package liric.mistaken.config

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * MessageConfig: Motor de internacionalización (i18n) ultra-optimizado.
 * Utiliza caché de componentes pre-renderizados para evitar procesar MiniMessage en cada tick.
 */
class MessageConfig(private val plugin: Mistaken) {

    private val mm = MiniMessage.miniMessage()
    private val languages = ConcurrentHashMap<String, FileConfiguration>()

    // Cachés de alto rendimiento
    private val componentCache = ConcurrentHashMap<String, MutableMap<String, Component>>()
    private val listCache = ConcurrentHashMap<String, MutableMap<String, List<Component>>>()
    private val rawStringCache = ConcurrentHashMap<String, MutableMap<String, String>>()

    private var defaultLang = "es"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadAllLanguages()
    }

    /**
     * Carga todos los archivos de idioma de forma asíncrona.
     */
    fun loadAllLanguages() {
        // Limpieza atómica de cachés
        languages.clear()
        componentCache.clear()
        listCache.clear()
        rawStringCache.clear()

        val langFolder = File(plugin.dataFolder, "lang")
        if (!langFolder.exists()) langFolder.mkdirs()

        // Exportar archivos base si no existen
        listOf("es", "en", "fr", "zh", "jp").forEach { lang ->
            val file = File(langFolder, "$lang.yml")
            if (!file.exists()) {
                plugin.saveResource("lang/$lang.yml", false)
            }
        }

        // Cargar archivos YAML al mapa de configuraciones
        langFolder.listFiles { _, name -> name.endsWith(".yml") }?.forEach { file ->
            val langName = file.nameWithoutExtension.lowercase()
            val config = YamlConfiguration.loadConfiguration(file)

            languages[langName] = config
            componentCache[langName] = ConcurrentHashMap()
            listCache[langName] = ConcurrentHashMap()
            rawStringCache[langName] = ConcurrentHashMap()
        }

        defaultLang = plugin.config.getString("settings.default-language", "es") ?: "es"
    }

    /**
     * Obtiene un String crudo. Ideal para BossBars o ActionBars que procesan placeholders manuales.
     */
    fun getRawString(player: Player?, path: String, def: String): String {
        val lang = getPlayerLang(player)
        val langMap = rawStringCache.getOrPut(lang) { ConcurrentHashMap() }

        return langMap.getOrPut(path) {
            val config = getLangConfig(lang)
            config.getString(path) ?: getLangConfig(defaultLang).getString(path, def) ?: def
        }
    }

    /**
     * Obtiene un mensaje traducido y procesado como Componente de Adventure.
     */
    fun getMessage(player: Player?, path: String, vararg tags: TagResolver): Component {
        val lang = getPlayerLang(player)

        // Si no hay tags dinámicos, usamos el caché de componentes pre-renderizados
        if (tags.isEmpty()) {
            val langMap = componentCache.getOrPut(lang) { ConcurrentHashMap() }
            return langMap.getOrPut(path) { buildComponent(lang, path) }
        }

        // Si hay tags, procesamos el mensaje de forma dinámica
        return processDynamic(lang, path, *tags)
    }

    /**
     * Obtiene una lista de componentes (Lores de items, Scoreboards, etc).
     */
    fun getMessageList(player: Player?, path: String): List<Component> {
        val lang = getPlayerLang(player)
        val langMap = listCache.getOrPut(lang) { ConcurrentHashMap() }

        return langMap.getOrPut(path) {
            val config = getLangConfig(lang)
            var rawLines = config.getStringList(path)

            if (rawLines.isEmpty()) {
                rawLines = getLangConfig(defaultLang).getStringList(path)
            }

            val prefixResolver = getPrefixResolver(config)
            rawLines.map { mm.deserialize(parseLegacy(it), prefixResolver) }
        }
    }

    private fun buildComponent(lang: String, path: String): Component {
        val config = getLangConfig(lang)
        val raw = config.getString(path) ?: getLangConfig(defaultLang).getString(path) ?: "<red>Missing Path: $path"

        return mm.deserialize(parseLegacy(raw), getPrefixResolver(config))
    }

    private fun processDynamic(lang: String, path: String, vararg tags: TagResolver): Component {
        val config = getLangConfig(lang)
        val raw = config.getString(path) ?: path

        // Combinamos el prefijo del idioma con los tags proporcionados
        val finalTags = arrayOf(getPrefixResolver(config), *tags)

        return mm.deserialize(parseLegacy(raw), *finalTags)
    }

    /**
     * Optimización: Convierte los viejos {tag} a <tag> de MiniMessage solo una vez.
     */
    private fun parseLegacy(text: String): String {
        return text.replace("{", "<").replace("}", ">")
    }

    /**
     * Genera un TagResolver para el prefijo de forma eficiente.
     */
    private fun getPrefixResolver(config: FileConfiguration): TagResolver {
        val prefixRaw = config.getString("prefix", "<b>MISTAKEN</b> | ") ?: ""
        return Placeholder.component("prefix", mm.deserialize(parseLegacy(prefixRaw)))
    }

    fun getLangConfig(lang: String): FileConfiguration {
        return languages[lang.lowercase()] ?: languages[defaultLang] ?: YamlConfiguration()
    }

    private fun getPlayerLang(player: Player?): String {
        return if (player == null) defaultLang
        else plugin.playerDataManager.getLanguage(player.uniqueId)
    }

    fun reload() {
        scope.launch { loadAllLanguages() }
    }
}
