package pumpking.lib.color

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.util.concurrent.ConcurrentHashMap

object ColorTranslator {
    private val mm = MiniMessage.miniMessage()
    
    // LRU Cache for high performance (limits to 1000 entries)
    private val cache = object : LinkedHashMap<String, Component>(1000, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Component>): Boolean {
            return size > 1000
        }
    }
    private val lock = Any()

    /**
     * Translates a string with mixed color formats (Legacy, Hex, MiniMessage) into a Component.
     */
    fun translate(input: String, vararg tags: TagResolver): Component {
        // If there are tags, we don't cache to prevent stale placeholders
        if (tags.isNotEmpty()) {
            val normalized = ColorNormalizer.normalizeToMiniMessage(input)
            return mm.deserialize(normalized, *tags)
        }

        // Use cache for raw strings without runtime tags
        synchronized(lock) {
            val cached = cache[input]
            if (cached != null) return cached

            val normalized = ColorNormalizer.normalizeToMiniMessage(input)
            val component = mm.deserialize(normalized)
            cache[input] = component
            return component
        }
    }
    
    /**
     * Translates a list of strings with mixed color formats.
     */
    fun translate(input: List<String>, vararg tags: TagResolver): List<Component> {
        return input.map { translate(it, *tags) }
    }
}
