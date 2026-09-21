package liric.mistaken.visual.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import liric.mistaken.api.MistakenProvider
import liric.mistaken.visual.VisualAddon
import liric.mistaken.visual.utils.VisualFormatUtil
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.Bukkit
import liric.mistaken.visual.utils.MiniPlaceholdersHook

class ChatListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val config = VisualAddon.instance.config
        
        // Check if API is loaded
        if (!MistakenProvider.isRegistered()) return

        // Find player game state
        val session = MistakenProvider.get().sessionManager.getSession(player)
        val stateName = session?.currentState?.name ?: "LOBBY"

        // Get format
        var format = config.getString("chat.formats.$stateName")
        if (format == null) {
            format = config.getString("chat.formats.DEFAULT", "<gray><player> <gray>» <white><message>")
        }
        
        val parsedFormat = VisualFormatUtil.parseFormat(player, format!!, isChat = true)
        
        val messageText = event.signedMessage().message()
        val isMiniPlaceholdersEnabled = Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")
        val globalPlaceholders = if (isMiniPlaceholdersEnabled) MiniPlaceholdersHook.getGlobalPlaceholders() else TagResolver.empty()

        event.renderer { source, sourceDisplayName, message, viewer ->
            val audiencePlaceholders = if (isMiniPlaceholdersEnabled) {
                MiniPlaceholdersHook.getAudiencePlaceholders(source)
            } else {
                TagResolver.empty()
            }

            val tags = TagResolver.resolver(
                Placeholder.component("player", sourceDisplayName),
                Placeholder.component("message", message),
                audiencePlaceholders,
                globalPlaceholders
            )

            MiniMessage.miniMessage().deserialize(parsedFormat, tags)
        }
    }
}
