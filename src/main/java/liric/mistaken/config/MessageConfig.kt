package liric.mistaken.config

import kotlinx.coroutines.*
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
 * MessageConfig Pro+: Motor de internacionalización multi-archivo.
 * Inyecta variables globales ({player}, {online}, {prefix}) automáticamente con Zero-Lag.
 */
class MessageConfig(private val plugin: Mistaken) {

    private val mm = plugin.mm
    // Estructura: Idioma -> NombreArchivo -> Config (Ej: "es" -> "asesinos_info" -> Yaml)
    private val langMap = ConcurrentHashMap<String, ConcurrentHashMap<String, FileConfiguration>>()
    private var defaultLang = "es"

    init {
        loadAllLanguages()
    }

    /**
     * 🔥 MOTOR DE AUTO-EXTRACCIÓN:
     * Saca todos los .yml de la carpeta 'langs/' dentro de tu JAR a la carpeta del servidor.
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
     * Carga y sincroniza todos los idiomas en la RAM (Carpetas y Archivos).
     */
    fun loadAllLanguages() {
        langMap.clear()
        extractYamlResources()

        val langFolder = File(plugin.dataFolder, "langs")
        if (!langFolder.exists()) langFolder.mkdirs()

        // Leemos las carpetas de idiomas (es, en, zh, jp)
        langFolder.listFiles { it.isDirectory }?.forEach { dir ->
            val langCode = dir.name.lowercase()
            val filesInDir = ConcurrentHashMap<String, FileConfiguration>()

            // Cargamos cada .yml que esté adentro (messages.yml, asesinos_info.yml)
            dir.walkTopDown().filter { it.extension == "yml" }.forEach { yamlFile ->
                val fileName = yamlFile.nameWithoutExtension.lowercase()
                filesInDir[fileName] = YamlConfiguration.loadConfiguration(yamlFile)
            }
            langMap[langCode] = filesInDir
        }

        defaultLang = plugin.config.getString("settings.default-language", "es") ?: "es"
        plugin.componentLogger.info(mm.deserialize("<gray>[I18n] Motor Pro+ sincronizado (${langMap.size} idiomas cargados).</gray>"))
    }

    /**
     * 🔥 LA CLAVE: Genera las variables que funcionarán en TODOS los mensajes.
     */
    private fun getGlobalResolvers(player: Player?, config: FileConfiguration): TagResolver {
        val prefixRaw = config.getString("prefix", "<b>MISTAKEN</b> | ") ?: ""

        return TagResolver.resolver(
            Placeholder.parsed("player", player?.name ?: "Consola"),
            Placeholder.parsed("online", Bukkit.getOnlinePlayers().size.toString()),
            Placeholder.component("prefix", mm.deserialize(parseLegacy(prefixRaw)))
        )
    }

    /**
     * Obtiene un mensaje del archivo 'messages.yml' por defecto.
     * (Mantiene la compatibilidad con todos tus comandos y listeners).
     */
    fun getMessage(player: Player?, path: String, vararg extraTags: TagResolver): Component {
        return getMessageFromFile(player, "messages", path, *extraTags)
    }

    /**
     * Obtiene un mensaje de un archivo específico (ej: "asesinos_info").
     */
    fun getMessageFromFile(player: Player?, fileName: String, path: String, vararg extraTags: TagResolver): Component {
        val config = getSpecificFile(player, fileName)
        var raw = config.getString(path)

        // Fallback al idioma por defecto si no lo encuentra en el idioma del jugador
        if (raw == null && getPlayerLang(player) != defaultLang) {
            raw = langMap[defaultLang]?.get(fileName.lowercase())?.getString(path)
        }

        val message = raw ?: "<red>Missing Path: $fileName -> $path"

        // Combinamos las variables globales con las extra (* spread operator)
        val allTags = TagResolver.resolver(getGlobalResolvers(player, config), *extraTags)

        return mm.deserialize(parseLegacy(message), allTags)
    }

    /**
     * Obtiene una lista de componentes (Lores). Usa "messages" por defecto si no le pasas archivo.
     */
    fun getMessageList(player: Player?, path: String, fileName: String = "messages"): List<Component> {
        val config = getSpecificFile(player, fileName)
        var rawList = config.getStringList(path)

        if (rawList.isEmpty() && getPlayerLang(player) != defaultLang) {
            rawList = langMap[defaultLang]?.get(fileName.lowercase())?.getStringList(path) ?: emptyList()
        }

        val globalTags = getGlobalResolvers(player, config)
        return rawList.map { mm.deserialize(parseLegacy(it), globalTags) }
    }

    /**
     * Obtiene un String crudo sin procesar (Para Scoreboards o variables de PacketEvents).
     */
    fun getRawString(player: Player?, path: String, def: String = "", fileName: String = "messages"): String {
        val config = getSpecificFile(player, fileName)
        return config.getString(path) ?: langMap[defaultLang]?.get(fileName.lowercase())?.getString(path) ?: def
    }

    /**
     * Convierte {variable} a <variable> para que MiniMessage la entienda.
     */
    /**
     * Convierte códigos legacy (&) y llaves ({}) al formato de MiniMessage (<>).
     * Esto quita los recuadros feos en la BossBar.
     */
    private fun parseLegacy(text: String): String {
        return text
            .replace("&", "§") // Primero aseguramos que todo sea § interno
            .replace("§(?=[0-9a-fk-or])".toRegex(), "") // ¡Cuello a los códigos legacy!
            .replace("{", "<")
            .replace("}", ">")
    }

    /**
     * Devuelve los idiomas cargados (Ej: ["es", "en", "zh"])
     */
    fun getLoadedLanguages(): Set<String> = langMap.keys

    /**
     * Consigue el YAML específico según el jugador y el nombre de archivo.
     */
    fun getSpecificFile(player: Player?, fileName: String): FileConfiguration {
        val lang = getPlayerLang(player)
        val safeFileName = fileName.lowercase()

        return langMap[lang]?.get(safeFileName)
            ?: langMap[defaultLang]?.get(safeFileName)
            ?: YamlConfiguration() // Archivo vacío si de plano no existe
    }

    private fun getPlayerLang(player: Player?): String {
        return player?.let { plugin.playerDataManager.getLanguage(it.uniqueId) } ?: defaultLang
    }

    /**
     * Recarga los idiomas de forma asíncrona.
     */
    fun reload() {
        CoroutineScope(Dispatchers.IO).launch {
            loadAllLanguages()
        }
    }
}
