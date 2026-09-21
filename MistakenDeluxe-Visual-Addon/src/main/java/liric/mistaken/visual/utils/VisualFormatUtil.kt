package liric.mistaken.visual.utils

import liric.mistaken.visual.VisualAddon
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

    fun parseFormat(player: Player, format: String, isChat: Boolean): String {
        var parsedFormat = format
        
        // PAPI support
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            parsedFormat = PlaceholderAPI.setPlaceholders(player, parsedFormat)
        }

        val configPath = if (isChat) "chat.legacy-colors" else "tablist.legacy-colors"
        if (VisualAddon.instance.config.getBoolean(configPath, true)) {
            val component = legacySerializer.deserialize(parsedFormat)
            parsedFormat = miniMessage.serialize(component)
            
            // Clean up backslashes that MiniMessage serializer might add to escape tags inside legacy text
            parsedFormat = parsedFormat.replace("\\<", "<").replace("\\>", ">")
        }

        return parsedFormat
    }
}
