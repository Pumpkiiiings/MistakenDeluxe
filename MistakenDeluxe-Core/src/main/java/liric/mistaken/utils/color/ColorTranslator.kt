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
     * Translates a string with mixed color formats, applying PlaceholderAPI placeholders first.
     */
    fun translate(player: org.bukkit.entity.Player?, input: String, vararg tags: TagResolver): Component {
        var parsed = input
        if (player != null && org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            parsed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, parsed)
        }
        
        // Include MiniPlaceholders universal tags automatically when player context is provided
        val allTags = mutableListOf(*tags)
        allTags.add(getUniversalTags(player))

        return translate(parsed, *allTags.toTypedArray())
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
        val list = mutableListOf<TagResolver>()
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            try {
                // Try 2.x API
                val clazz = Class.forName("io.github.miniplaceholders.api.MiniPlaceholders")
                
                if (player != null) {
                    try {
                        val getAudienceMethod = clazz.getMethod("getAudienceGlobalPlaceholders", org.bukkit.entity.Player::class.java)
                        list.add(getAudienceMethod.invoke(null, player) as TagResolver)
                    } catch (e: Exception) {
                        // 3.x API Audience method
                        try {
                            val getAudienceMethod = clazz.getMethod("getAudiencePlaceholders", org.bukkit.entity.Player::class.java)
                            list.add(getAudienceMethod.invoke(null, player) as TagResolver)
                        } catch (e2: Exception) {
                            try {
                                val getAudienceMethod = clazz.getMethod("getAudienceGlobalPlaceholders", net.kyori.adventure.audience.Audience::class.java)
                                list.add(getAudienceMethod.invoke(null, player) as TagResolver)
                            } catch (e3: Exception) {
                                // 3.x API no-arg
                                try {
                                    val getAudienceMethod = clazz.getMethod("audienceGlobalPlaceholders")
                                    list.add(getAudienceMethod.invoke(null) as TagResolver)
                                } catch (e4: Exception) {
                                    try {
                                        val getAudienceMethod = clazz.getMethod("audiencePlaceholders")
                                        list.add(getAudienceMethod.invoke(null) as TagResolver)
                                    } catch (e5: Exception) {
                                    }
                                }
                            }
                        }
                    }
                } else {
                    try {
                        val getGlobalMethod = clazz.getMethod("getGlobalPlaceholders")
                        list.add(getGlobalMethod.invoke(null) as TagResolver)
                    } catch (e: Exception) {
                        // 3.x API
                        try {
                            val getGlobalMethod = clazz.getMethod("globalPlaceholders")
                            list.add(getGlobalMethod.invoke(null) as TagResolver)
                        } catch (e2: Exception) {}
                    }
                }
            } catch (e: Exception) {
                liric.mistaken.MistakenLib.logError(liric.mistaken.MistakenLib.LogCategory.CORE, "[WARN] Failed to load MiniPlaceholders tags via Reflection.")
            }
        }
        return TagResolver.resolver(list)
    }
}
