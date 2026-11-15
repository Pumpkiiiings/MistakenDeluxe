package liric.mistaken.visual

import io.papermc.paper.event.player.AsyncChatEvent
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class ChatListener(private val plugin: Mistaken) : Listener {

    private val miniMessage = MiniMessage.miniMessage()

    fun isChatEnabled(): Boolean {
        val cfg = plugin.config
        return cfg.getBoolean("visuals.chat.enabled", cfg.getBoolean("visual.chat.enabled", cfg.getBoolean("chat.enabled", true)))
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        if (!isChatEnabled()) return

        val player = event.player
        val config = plugin.config

        // Find player game state
        val session = plugin.sessionManager.getSession(player)
        val stateName = session?.currentState?.name ?: "LOBBY"

        // Get format with fallbacks
        val format = config.getString("visuals.chat.formats.$stateName")
            ?: config.getString("visual.chat.formats.$stateName")
            ?: config.getString("chat.formats.$stateName")
            ?: config.getString("visuals.chat.formats.DEFAULT")
            ?: config.getString("visual.chat.formats.DEFAULT")
            ?: config.getString("chat.formats.DEFAULT")
            ?: "<gray><player> <gray>» <white><message>"

        val parsedFormat = VisualFormatUtil.parseFormat(player, format, isChat = true, plugin = plugin)
        val isMiniPlaceholdersEnabled = Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")
        val globalPlaceholders = if (isMiniPlaceholdersEnabled) MiniPlaceholdersHook.getGlobalPlaceholders() else TagResolver.empty()

        event.renderer { source, sourceDisplayName, message, _ ->
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

            miniMessage.deserialize(parsedFormat, tags)
        }
    }
}
