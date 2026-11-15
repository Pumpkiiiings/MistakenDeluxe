package liric.mistaken.visual

import io.github.miniplaceholders.api.MiniPlaceholders
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.entity.Player
import java.lang.reflect.Method

object MiniPlaceholdersHook {

    private val globalMethod: Method? by lazy {
        findMethod("getGlobalPlaceholders") ?: findMethod("globalPlaceholders")
    }

    private val audienceMethod: Method? by lazy {
        findMethod("getAudienceGlobalPlaceholders", Player::class.java)
            ?: findMethod("getAudiencePlaceholders", Player::class.java)
            ?: findMethod("getAudienceGlobalPlaceholders", net.kyori.adventure.audience.Audience::class.java)
            ?: findMethod("audienceGlobalPlaceholders")
            ?: findMethod("audiencePlaceholders")
    }

    private fun findMethod(name: String, vararg paramTypes: Class<*>): Method? {
        return try {
            MiniPlaceholders::class.java.getMethod(name, *paramTypes)
        } catch (_: Exception) {
            null
        }
    }

    fun getGlobalPlaceholders(): TagResolver {
        val method = globalMethod ?: return TagResolver.empty()
        return try {
            method.invoke(null) as? TagResolver ?: TagResolver.empty()
        } catch (_: Exception) {
            TagResolver.empty()
        }
    }

    fun getAudiencePlaceholders(player: Player): TagResolver {
        val method = audienceMethod ?: return TagResolver.empty()
        return try {
            if (method.parameterCount == 1) {
                method.invoke(null, player) as? TagResolver ?: TagResolver.empty()
            } else {
                method.invoke(null) as? TagResolver ?: TagResolver.empty()
            }
        } catch (_: Exception) {
            TagResolver.empty()
        }
    }
}
