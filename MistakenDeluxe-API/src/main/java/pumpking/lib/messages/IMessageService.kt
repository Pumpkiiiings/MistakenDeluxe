package pumpking.lib.messages

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player

/**
 * Interface for MessageService to be accessible from the API module.
 */
interface IMessageService {
    fun getSpecificFile(player: Player?, fileName: String): FileConfiguration
    fun getComponent(player: Player?, path: String, vararg extraTags: TagResolver): Component
    fun getComponentFromFile(player: Player?, fileName: String, path: String, vararg extraTags: TagResolver): Component
    fun getComponentList(player: Player?, path: String, fileName: String = "messages"): List<Component>
    fun getRawString(player: Player?, path: String, def: String, fileName: String = "messages"): String
    fun getRawStringList(player: Player?, path: String, fileName: String = "messages"): List<String>
    
    // Strict config loading for Character data, avoids default fallbacks and prints Missing Path if absent.
    fun getStrictString(player: Player?, path: String, fileName: String): String
    fun getStrictStringList(player: Player?, path: String, fileName: String): List<String>

    fun send(player: Player, path: String, vararg extraTags: TagResolver)
    fun actionBar(player: Player, path: String, vararg extraTags: TagResolver)
    fun title(player: Player, titlePath: String, subtitlePath: String? = null, fadeIn: Int = 10, stay: Int = 70, fadeOut: Int = 20, vararg extraTags: TagResolver)
    fun bossBar(player: Player, path: String, color: net.kyori.adventure.bossbar.BossBar.Color = net.kyori.adventure.bossbar.BossBar.Color.WHITE, overlay: net.kyori.adventure.bossbar.BossBar.Overlay = net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS, vararg extraTags: TagResolver): net.kyori.adventure.bossbar.BossBar

    fun reload()
    fun loadAllLanguages()
    fun getLoadedLanguages(): Set<String>
}
