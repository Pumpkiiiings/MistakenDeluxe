package pumpking.lib.color

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.util.concurrent.ConcurrentHashMap

object ColorTranslator {
    private val mm = MiniMessage.miniMessage()

    // FIX #4: Replace LinkedHashMap + synchronized(lock) with a ConcurrentHashMap.
    // The previous approach used a global lock that caused contention on the scoreboard
    // hot path (every tick, every player). ConcurrentHashMap.computeIfAbsent() is lock-free
    // for most reads and uses stripe-level locking only on first insertion.
    // Simple bounded eviction: clear the map when it reaches the size limit.
    // This is not LRU but is fully thread-safe and sufficient for this use case.
    private val cache = ConcurrentHashMap<String, Component>(1024)

    /**
     * Translates a string with mixed color formats (Legacy, Hex, MiniMessage) into a Component.
     */
    fun translate(input: String, vararg tags: TagResolver): Component {
        // If there are tags, skip the cache to prevent stale placeholder values
        if (tags.isNotEmpty()) {
            val normalized = ColorNormalizer.normalizeToMiniMessage(input)
            return pumpking.lib.color.ColorTranslator.translate(normalized, *tags)
        }

        // FIX #4: Evict before inserting to keep the map bounded.
        // The TOCTOU gap is acceptable: worst case we exceed 1000 entries by the
        // number of concurrent inserting threads — negligible for this use case.
        if (cache.size >= 1000) cache.clear()
        return cache.computeIfAbsent(input) { k ->
            pumpking.lib.color.ColorTranslator.translate(ColorNormalizer.normalizeToMiniMessage(k))
        }
    }

    /**
     * Translates a list of strings with mixed color formats.
     */
    fun translate(input: List<String>, vararg tags: TagResolver): List<Component> {
        return input.map { translate(it, *tags) }
    }
}
