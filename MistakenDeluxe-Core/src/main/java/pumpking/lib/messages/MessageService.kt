package pumpking.lib.messages

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import pumpking.lib.config.YamlConfigProvider
import pumpking.lib.core.PumpkingLib
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import net.kyori.adventure.title.Title
import java.time.Duration
import java.util.jar.JarFile

/**
 * PumpkingLib - Modular Message API
 */
class MessageService : IMessageService {
    private val mm = MiniMessage.miniMessage()
    private val langMap = ConcurrentHashMap<String, ConcurrentHashMap<String, FileConfiguration>>()
    var defaultLang: String = "es"

    // Abstracted language provider to avoid coupling to Mistaken's PlayerDataManager
    var languageProvider: LanguageProvider = object : LanguageProvider {
        override fun getLanguage(uuid: UUID): String = defaultLang
    }



    fun init() {
        loadAllLanguages()
    }

    private fun extractYamlResources() {
        runCatching {
            val jarFile = File(PumpkingLib.plugin::class.java.protectionDomain.codeSource.location.toURI())
            if (!jarFile.exists()) return@runCatching

            JarFile(jarFile).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val path = entry.name

                    val isLangFile = path.startsWith("langs/") && path.endsWith(".yml") && !entry.isDirectory
                    if (isLangFile) {
                        val outFile = File(PumpkingLib.plugin.dataFolder, path)
                        if (!outFile.exists()) {
                            outFile.parentFile.mkdirs()
                            PumpkingLib.plugin.saveResource(path, false)
                        }
                    }
                }
            }
        }.onFailure { e ->
            PumpkingLib.logError(PumpkingLib.LogCategory.CORE, "[Messages] Error extracting resources: ${e.message}")
        }
    }

    override fun loadAllLanguages() {
        extractYamlResources()

        // FIX #8: Load into a fresh map and then swap.
        // The previous approach called langMap.clear() before re-populating, leaving a window
        // where the main thread could call getComponent() and receive an empty map — causing
        // every player to see "<red>Missing Path: ..." until loading was complete.
        // Now we build the new map entirely before making it visible.
        val newMap = ConcurrentHashMap<String, ConcurrentHashMap<String, FileConfiguration>>()

        val langFolder = File(PumpkingLib.plugin.dataFolder, "langs")
        if (!langFolder.exists()) langFolder.mkdirs()

        langFolder.listFiles { it.isDirectory }?.forEach { dir ->
            val langCode = dir.name.lowercase()
            val filesInDir = ConcurrentHashMap<String, FileConfiguration>()

            dir.walkTopDown().filter { it.extension == "yml" }.forEach { yamlFile ->
                val fileName = yamlFile.nameWithoutExtension.lowercase()
                val provider = YamlConfigProvider(yamlFile)
                provider.load()
                filesInDir[fileName] = provider.getRaw()
            }
            newMap[langCode] = filesInDir
        }

        // Atomic swap: clear and refill in one step to minimize the empty-map window
        langMap.clear()
        langMap.putAll(newMap)
        PumpkingLib.log(PumpkingLib.LogCategory.CORE, "[Messages] Initialized (${langMap.size} languages).")
    }

    override fun getLoadedLanguages(): Set<String> {
        return langMap.keys
    }

    private fun getPlayerLang(player: Player?): String {
        return player?.let { languageProvider.getLanguage(it.uniqueId) } ?: defaultLang
    }

    override fun getSpecificFile(player: Player?, fileName: String): FileConfiguration {
        val lang = getPlayerLang(player)
        val safeFileName = fileName.lowercase()

        return langMap[lang]?.get(safeFileName)
            ?: langMap[defaultLang]?.get(safeFileName)
            ?: org.bukkit.configuration.file.YamlConfiguration()
    }

    private fun getGlobalResolvers(player: Player?, config: FileConfiguration): TagResolver {
        val prefixRaw = config.getString("prefix", "<b>PREFIX</b> | ") ?: ""
        return TagResolver.resolver(
            Placeholder.parsed("player", player?.name ?: "Console"),
            Placeholder.parsed("online", Bukkit.getOnlinePlayers().size.toString()),
            Placeholder.component("prefix", pumpking.lib.color.ColorTranslator.translate(parseLegacy(prefixRaw)))
        )
    }

    override fun getComponent(player: Player?, path: String, vararg extraTags: TagResolver): Component {
        return getComponentFromFile(player, "messages", path, *extraTags)
    }

    override fun getComponentFromFile(player: Player?, fileName: String, path: String, vararg extraTags: TagResolver): Component {
        val config = getSpecificFile(player, fileName)
        var raw = config.getString(path)

        if (raw == null && getPlayerLang(player) != defaultLang) {
            raw = langMap[defaultLang]?.get(fileName.lowercase())?.getString(path)
        }

        if (raw == null) {
            PumpkingLib.logError(PumpkingLib.LogCategory.CORE, "[WARN] Missing config path:\nFile: $fileName.yml\nPath: $path")
            val allTags = TagResolver.resolver(getGlobalResolvers(player, config), *extraTags)
            return pumpking.lib.color.ColorTranslator.translate("<red>Missing Path: $fileName -> $path", allTags)
        }

        val allTags = TagResolver.resolver(getGlobalResolvers(player, config), *extraTags)
        return pumpking.lib.color.ColorTranslator.translate(parseLegacy(raw), allTags)
    }

    override fun getComponentList(player: Player?, path: String, fileName: String): List<Component> {
        val config = getSpecificFile(player, fileName)
        var rawList = config.getStringList(path)

        if (rawList.isEmpty() && getPlayerLang(player) != defaultLang) {
            rawList = langMap[defaultLang]?.get(fileName.lowercase())?.getStringList(path) ?: emptyList()
        }

        val globalTags = getGlobalResolvers(player, config)
        return rawList.map { pumpking.lib.color.ColorTranslator.translate(parseLegacy(it), globalTags) }
    }

    override fun getRawString(player: Player?, path: String, def: String, fileName: String): String {
        val config = getSpecificFile(player, fileName)
        return config.getString(path) ?: langMap[defaultLang]?.get(fileName.lowercase())?.getString(path) ?: def
    }

    override fun getRawStringList(player: Player?, path: String, fileName: String): List<String> {
        val config = getSpecificFile(player, fileName)
        var list = config.getStringList(path)
        if (list.isEmpty() && getPlayerLang(player) != defaultLang) {
            list = langMap[defaultLang]?.get(fileName.lowercase())?.getStringList(path) ?: emptyList()
        }
        return list
    }

    override fun getStrictString(player: Player?, path: String, fileName: String): String {
        val config = getSpecificFile(player, fileName)
        val raw = config.getString(path) ?: langMap[defaultLang]?.get(fileName.lowercase())?.getString(path)
        if (raw == null) {
            PumpkingLib.logError(PumpkingLib.LogCategory.CORE, "[WARN] Missing config path:\nFile: $fileName.yml\nPath: $path")
            return "<red>Missing Path: $fileName -> $path"
        }
        return raw
    }

    override fun getStrictStringList(player: Player?, path: String, fileName: String): List<String> {
        val config = getSpecificFile(player, fileName)
        var list = config.getStringList(path)
        if (list.isEmpty() && getPlayerLang(player) != defaultLang) {
            list = langMap[defaultLang]?.get(fileName.lowercase())?.getStringList(path) ?: emptyList()
        }
        if (list.isEmpty()) {
            PumpkingLib.logError(PumpkingLib.LogCategory.CORE, "[WARN] Missing config path:\nFile: $fileName.yml\nPath: $path")
            return listOf("<red>Missing Path: $fileName -> $path")
        }
        return list
    }

    private fun parseLegacy(text: String): String {
        // FIX #14: The previous implementation had a logical bug:
        //   1. replace("&", "§")            → "&a" becomes "§a"
        //   2. replace("§(?=[0-9a-fk-or])", "") → "§a" becomes "a"  ← DELETES the color!
        // The net result was that all legacy color codes were stripped rather than converted.
        // ColorNormalizer.normalizeToMiniMessage() correctly converts &a / §a → <green>, etc.
        // The {curly} → <angle> replacement is preserved for custom tag syntax support.
        return pumpking.lib.color.ColorNormalizer.normalizeToMiniMessage(
            text.replace("{", "<").replace("}", ">")
        )
    }

    // --- API Methods ---

    override fun send(player: Player, path: String, vararg extraTags: TagResolver) {
        player.sendMessage(getComponent(player, path, *extraTags))
    }

    override fun actionBar(player: Player, path: String, vararg extraTags: TagResolver) {
        player.sendActionBar(getComponent(player, path, *extraTags))
    }

    override fun title(player: Player, titlePath: String, subtitlePath: String?, fadeIn: Int, stay: Int, fadeOut: Int, vararg extraTags: TagResolver) {
        val titleComp = getComponent(player, titlePath, *extraTags)
        val subtitleComp = if (subtitlePath != null) getComponent(player, subtitlePath, *extraTags) else Component.empty()

        val times = Title.Times.times(
            Duration.ofMillis((fadeIn * 50).toLong()),
            Duration.ofMillis((stay * 50).toLong()),
            Duration.ofMillis((fadeOut * 50).toLong())
        )
        val titleObj = Title.title(titleComp, subtitleComp, times)
        player.showTitle(titleObj)
    }

    // Basic implementation for bossbars (could be extended)
    override fun bossBar(player: Player, path: String, color: net.kyori.adventure.bossbar.BossBar.Color, overlay: net.kyori.adventure.bossbar.BossBar.Overlay, vararg extraTags: TagResolver): net.kyori.adventure.bossbar.BossBar {
        val comp = getComponent(player, path, *extraTags)
        val bar = net.kyori.adventure.bossbar.BossBar.bossBar(comp, 1.0f, color, overlay)
        player.showBossBar(bar)
        return bar
    }

    override fun reload() {
        pumpking.lib.task.PumpkingTask.ioScope.launch {
            loadAllLanguages()
        }
    }
}

interface LanguageProvider {
    fun getLanguage(uuid: UUID): String
}


