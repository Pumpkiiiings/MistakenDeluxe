package liric.mistaken.utils.color

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object ColorTranslator {
    private val mm = MiniMessage.miniMessage()

    private val cache = ConcurrentHashMap<String, Component>(1024)

    private data class MiniPlaceholdersMethods(
        val audienceMethod: Method?,
        val audienceTakesPlayer: Boolean,
        val globalMethod: Method?
    )

    private val miniPlaceholdersMethods: MiniPlaceholdersMethods by lazy { resolveMiniPlaceholdersMethods() }

    private fun resolveMiniPlaceholdersMethods(): MiniPlaceholdersMethods {
        return try {
            val clazz = Class.forName("io.github.miniplaceholders.api.MiniPlaceholders")
            val (audMethod, takesPlayer) = findAudienceMethod(clazz)
            val globalMethod = findGlobalMethod(clazz)
            MiniPlaceholdersMethods(audMethod, takesPlayer, globalMethod)
        } catch (e: Exception) {
            liric.mistaken.MistakenLib.logError(
                liric.mistaken.MistakenLib.LogCategory.CORE,
                "[WARN] Failed to resolve MiniPlaceholders methods via Reflection."
            )
            MiniPlaceholdersMethods(null, false, null)
        }
    }

    private fun findAudienceMethod(clazz: Class<*>): Pair<Method?, Boolean> {
        val candidates: List<Pair<String, Array<Class<*>>>> = listOf(
            "getAudienceGlobalPlaceholders" to arrayOf(Player::class.java),
            "getAudiencePlaceholders" to arrayOf(Player::class.java),
            "getAudienceGlobalPlaceholders" to arrayOf(net.kyori.adventure.audience.Audience::class.java),
            "audienceGlobalPlaceholders" to emptyArray(),
            "audiencePlaceholders" to emptyArray()
        )
        for ((name, params) in candidates) {
            try {
                return clazz.getMethod(name, *params) to params.isNotEmpty()
            } catch (_: NoSuchMethodException) {}
        }
        return null to false
    }

    private fun findGlobalMethod(clazz: Class<*>): Method? {
        for (name in listOf("getGlobalPlaceholders", "globalPlaceholders")) {
            try {
                return clazz.getMethod(name)
            } catch (_: NoSuchMethodException) {}
        }
        return null
    }

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
    fun translate(player: Player?, input: String, vararg tags: TagResolver): Component {
        var parsed = input
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            parsed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, parsed)
        }

        val universal = getUniversalTags(player)
        return if (universal === TagResolver.empty()) {
            translate(parsed, *tags)
        } else {
            translate(parsed, *tags, universal)
        }
    }

    /**
     * Translates a list of strings with mixed color formats.
     */
    fun translate(input: List<String>, vararg tags: TagResolver): List<Component> {
        return input.map { translate(it, *tags) }
    }

    /**
     * Gets universal TagResolvers (like MiniPlaceholders) for a player using cached reflection handles.
     */
    fun getUniversalTags(player: Player?): TagResolver {
        if (!Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            return TagResolver.empty()
        }
        return try {
            val methods = miniPlaceholdersMethods
            val resolved = if (player != null) {
                val m = methods.audienceMethod ?: return TagResolver.empty()
                if (methods.audienceTakesPlayer) m.invoke(null, player) else m.invoke(null)
            } else {
                val m = methods.globalMethod ?: return TagResolver.empty()
                m.invoke(null)
            }
            (resolved as? TagResolver) ?: TagResolver.empty()
        } catch (e: Exception) {
            TagResolver.empty()
        }
    }
}
