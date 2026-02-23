package liric.mistaken.config

import liric.mistaken.Mistaken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
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
 * MessageConfig Pro+: Motor de internacionalización con Inyector de Variables.
 * Reemplaza automáticamente {player}, {online}, {prefix} y más en todo el plugin.
 */
class MessageConfig(private val plugin: Mistaken) {

    private val mm = MiniMessage.miniMessage()
    private val langMap = ConcurrentHashMap<String, ConcurrentHashMap<String, FileConfiguration>>()
    private var defaultLang = "es"

    init {
        loadAllLanguages()
    }

    /**
     * 🔥 MOTOR DE AUTO-EXTRACCIÓN:
     * Extrae recursivamente todo lo que esté en 'langs/' dentro del JAR.
     */
    private fun extractYamlResources() {
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
            plugin.componentLogger.error(mm.deserialize("<red>[I18n] Error crítico extrayendo recursos: ${e.message}"))
        }
    }

    /**
     * Carga y sincroniza todos los idiomas en la RAM.
     */
    fun loadAllLanguages() {
        langMap.clear()
        extractYamlResources()

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
        plugin.componentLogger.info(mm.deserialize("<gray>[I18n] Motor Pro+ sincronizado con Inyector de Variables.</gray>"))
    }

    /**
     * 🔥 LA CLAVE: Genera las variables que funcionarán en TODOS los mensajes.
     */
    private fun getGlobalResolvers(player: Player?, config: FileConfiguration): TagResolver {
        val resolvers = mutableListOf<TagResolver>()

        // 1. Variable {player}
        resolvers.add(Placeholder.parsed("player", player?.name ?: "Consola"))

        // 2. Variable {prefix} (jala del archivo del idioma actual)
        val prefixRaw = config.getString("prefix", "") ?: ""
        resolvers.add(Placeholder.component("prefix", mm.deserialize(parseLegacy(prefixRaw))))

        // 3. Variable {online}
        resolvers.add(Placeholder.parsed("online", Bukkit.getOnlinePlayers().size.toString()))

        return TagResolver.resolver(resolvers)
    }

    /**
     * Obtiene un mensaje traducido y procesa variables automáticamente.
     */
    fun getMessage(player: Player?, path: String, vararg extraTags: TagResolver): Component {
        val lang = getPlayerLang(player)
        val config = getSpecificFile(player, lang)

        var raw = config.getString(path)
        if (raw == null && lang != defaultLang) {
            raw = langMap[defaultLang]?.get(defaultLang)?.getString(path)
        }

        val message = raw ?: "<red>Missing Path: $path"

        // Combinamos variables globales con las extra que pases por código
        val allTags = TagResolver.resolver(getGlobalResolvers(player, config), TagResolver.resolver(extraTags.toList()))

        return mm.deserialize(parseLegacy(message), allTags)
    }

    /**
     * Obtiene una lista de componentes (Lores) con variables inyectadas.
     */
    fun getMessageList(player: Player?, fileName: String, path: String): List<Component> {
        val config = getSpecificFile(player, fileName)
        val rawList = config.getStringList(path).ifEmpty {
            if (getPlayerLang(player) != defaultLang)
                langMap[defaultLang]?.get(fileName.lowercase())?.getStringList(path) ?: emptyList()
            else emptyList()
        }

        val globalTags = getGlobalResolvers(player, config)
        return rawList.map { mm.deserialize(parseLegacy(it), globalTags) }
    }

    /**
     * 🔥 ÚTIL PARA MENÚS: Procesa un texto que ya tienes (ej: nombre de asesino)
     * para que acepte variables como {player}.
     */
    fun parseCustomText(player: Player?, text: String): Component {
        val lang = getPlayerLang(player)
        val config = getSpecificFile(player, lang)
        return mm.deserialize(parseLegacy(text), getGlobalResolvers(player, config))
    }

    fun getRawString(player: Player?, fileName: String, path: String, def: String = ""): String {
        val config = getSpecificFile(player, fileName)
        return config.getString(path) ?: def
    }

    /**
     * Convierte {variable} a <variable> para que MiniMessage la entienda.
     */
    private fun parseLegacy(text: String): String {
        return text.replace("{", "<").replace("}", ">")
    }

    fun getLoadedLanguages(): Set<String> = langMap.keys

    fun getSpecificFile(player: Player?, fileName: String): FileConfiguration {
        val lang = getPlayerLang(player)
        return langMap[lang]?.get(fileName.lowercase())
            ?: langMap[defaultLang]?.get(fileName.lowercase())
            ?: YamlConfiguration()
    }

    private fun getPlayerLang(player: Player?): String {
        return player?.let { plugin.playerDataManager.getLanguage(it.uniqueId) } ?: defaultLang
    }

    fun reload() = loadAllLanguages()
}
