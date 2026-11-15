package liric.mistaken.visual

import liric.mistaken.Mistaken
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.minimessage.MiniMessage

object VisualFormatUtil {

    private val legacySerializer = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .extractUrls()
        .build()
        
    private val miniMessage = MiniMessage.miniMessage()

    fun parseFormat(player: Player, format: String, isChat: Boolean, plugin: Mistaken): String {
        var parsedFormat = format
        
        // PAPI support
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            parsedFormat = PlaceholderAPI.setPlaceholders(player, parsedFormat)
        }

        val config = plugin.config
        val legacyColors = if (isChat) {
            config.getBoolean("visuals.chat.legacy-colors", config.getBoolean("visual.chat.legacy-colors", config.getBoolean("chat.legacy-colors", true)))
        } else {
            config.getBoolean("visuals.tab.legacy-colors", config.getBoolean("visual.tab.legacy-colors", config.getBoolean("tablist.legacy-colors", true)))
        }

        if (legacyColors) {
            val component = legacySerializer.deserialize(parsedFormat)
            parsedFormat = miniMessage.serialize(component)
            
            // Clean up backslashes that MiniMessage serializer might add to escape tags inside legacy text
            parsedFormat = parsedFormat.replace("\\<", "<").replace("\\>", ">")
        }

        return parsedFormat
    }
}
