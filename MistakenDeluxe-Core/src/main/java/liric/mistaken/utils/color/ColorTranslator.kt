package liric.mistaken.utils.color

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.util.concurrent.ConcurrentHashMap

object ColorTranslator {
    private val mm = MiniMessage.miniMessage()

    
    
    
    
    
    
    private val cache = ConcurrentHashMap<String, Component>(1024)

    /**
     * Translates a string with mixed color formats (Legacy, Hex, MiniMessage) into a Component.
     */
    fun translate(input: String, vararg tags: TagResolver): Component {
        
        if (tags.isNotEmpty()) {
            val normalized = "<!italic>" + ColorNormalizer.normalizeToMiniMessage(input)
            return mm.deserialize(normalized, *tags)
        }

        
        
        
        if (cache.size >= 1000) cache.clear()
        return cache.computeIfAbsent(input) { k ->
            mm.deserialize("<!italic>" + ColorNormalizer.normalizeToMiniMessage(k))
        }
    }

    /**
     * Translates a list of strings with mixed color formats.
     */
    fun translate(input: List<String>, vararg tags: TagResolver): List<Component> {
        return input.map { translate(it, *tags) }
    }

    /**
     * Gets universal TagResolvers (like MiniPlaceholders) for a player.
     */
    fun getUniversalTags(player: org.bukkit.entity.Player?): TagResolver {
        val resolvers = mutableListOf<TagResolver>()
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            if (player != null) {
                resolvers.add(io.github.miniplaceholders.api.MiniPlaceholders.getAudiencePlaceholders(player))
            }
            resolvers.add(io.github.miniplaceholders.api.MiniPlaceholders.getGlobalPlaceholders())
        }
        return TagResolver.resolver(resolvers)
    }
}
