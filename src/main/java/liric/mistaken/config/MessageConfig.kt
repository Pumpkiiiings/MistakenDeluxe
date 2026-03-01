package liric.mistaken.config

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

/**
 * [LIRIC-MISTAKEN 2.0]
 * MessageConfig Pro+: Motor de internacionalización multi-archivo.
 * FIX: Extracción de recursos asíncrona y carga no bloqueante.
 */
class MessageConfig(private val plugin: Mistaken) {

    private val mm = plugin.mm
    // Estructura: Idioma -> NombreArchivo -> Config
    private val langMap = ConcurrentHashMap<String, ConcurrentHashMap<String, FileConfiguration>>()
    private var defaultLang = "es"

    // Scope para operaciones de disco (I/O)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Carga inicial rápida (bloqueante solo lo necesario)
        loadAllLanguages()
    }

    /**
     * Motor de auto-extracción optimizado.
     * Se ejecuta en segundo plano para no frenar el inicio del servidor.
     */
    private fun extractYamlResources() {
        ioScope.launch {
            runCatching {
                val jarFile = File(plugin::class.java.protectionDomain.codeSource.location.toURI())
                if (!jarFile.exists()) return@runCatching

                JarFile(jarFile).use { jar ->
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val path = entry.name

                        if (path.startsWith("langs/") && path.endsWith(".yml") && !entry.isDirectory) {
                            val outFile = File(plugin.dataFolder, path)
                            if (!outFile.exists()) {
                                outFile.parentFile.mkdirs()
                                plugin.saveResource(path, false)
                            }
                        }
                    }
                }
            }.onFailure { e ->
                plugin.componentLogger.error(mm.deserialize("<red>[I18n] Error extrayendo recursos: ${e.message}</red>"))
            }
        }
    }

    /**
     * Carga y sincroniza todos los idiomas.
     */
    fun loadAllLanguages() {
        // Primero lanzamos la extracción en fondo
        extractYamlResources()

        // Cargamos lo que ya existe en disco (rápido)
        langMap.clear()
        val langFolder = File(plugin.dataFolder, "langs")
        if (!langFolder.exists()) langFolder.mkdirs()

        langFolder.listFiles { it.isDirectory }?.forEach { dir ->
            val langCode = dir.name.lowercase()
            val filesInDir = ConcurrentHashMap<String, FileConfiguration>()

            dir.walkTopDown().filter { it.extension == "yml" }.forEach { yamlFile ->
                val fileName = yamlFile.nameWithoutExtension.lowercase()
                filesInDir[fileName] = YamlConfiguration.loadConfiguration(yamlFile)
            }
            langMap[langCode] = filesInDir
        }

        defaultLang = plugin.config.getString("settings.default-language", "es") ?: "es"
        plugin.componentLogger.info(mm.deserialize("<gray>[I18n] Motor sincronizado (${langMap.size} idiomas).</gray>"))
    }

    fun getLoadedLanguages(): Set<String> = langMap.keys

    /**
     * Genera variables globales ({player}, {online}, {prefix}).
     */
    private fun getGlobalResolvers(player: Player?, config: FileConfiguration): TagResolver {
        val prefixRaw = config.getString("prefix", "<b>MISTAKEN</b> | ") ?: ""
        return TagResolver.resolver(
            Placeholder.parsed("player", player?.name ?: "Consola"),
            Placeholder.parsed("online", Bukkit.getOnlinePlayers().size.toString()),
            Placeholder.component("prefix", mm.deserialize(parseLegacy(prefixRaw)))
        )
    }

    fun getMessage(player: Player?, path: String, vararg extraTags: TagResolver): Component {
        return getMessageFromFile(player, "messages", path, *extraTags)
    }

    fun getMessageFromFile(player: Player?, fileName: String, path: String, vararg extraTags: TagResolver): Component {
        val config = getSpecificFile(player, fileName)
        var raw = config.getString(path)

        if (raw == null && getPlayerLang(player) != defaultLang) {
            raw = langMap[defaultLang]?.get(fileName.lowercase())?.getString(path)
        }

        val message = raw ?: "<red>Missing Path: $fileName -> $path"
        val allTags = TagResolver.resolver(getGlobalResolvers(player, config), *extraTags)

        return mm.deserialize(parseLegacy(message), allTags)
    }

    fun getMessageList(player: Player?, path: String, fileName: String = "messages"): List<Component> {
        val config = getSpecificFile(player, fileName)
        var rawList = config.getStringList(path)

        if (rawList.isEmpty() && getPlayerLang(player) != defaultLang) {
            rawList = langMap[defaultLang]?.get(fileName.lowercase())?.getStringList(path) ?: emptyList()
        }

        val globalTags = getGlobalResolvers(player, config)
        return rawList.map { mm.deserialize(parseLegacy(it), globalTags) }
    }

    fun getRawString(player: Player?, path: String, def: String = "", fileName: String = "messages"): String {
        val config = getSpecificFile(player, fileName)
        return config.getString(path) ?: langMap[defaultLang]?.get(fileName.lowercase())?.getString(path) ?: def
    }

    private fun parseLegacy(text: String): String {
        return text
            .replace("&", "§")
            .replace("§(?=[0-9a-fk-or])".toRegex(), "")
            .replace("{", "<")
            .replace("}", ">")
    }

    fun getSpecificFile(player: Player?, fileName: String): FileConfiguration {
        val lang = getPlayerLang(player)
        val safeFileName = fileName.lowercase()

        return langMap[lang]?.get(safeFileName)
            ?: langMap[defaultLang]?.get(safeFileName)
            ?: YamlConfiguration()
    }

    private fun getPlayerLang(player: Player?): String {
        return player?.let { plugin.playerDataManager.getLanguage(it.uniqueId) } ?: defaultLang
    }

    fun reload() {
        ioScope.launch {
            loadAllLanguages()
        }
    }
}
