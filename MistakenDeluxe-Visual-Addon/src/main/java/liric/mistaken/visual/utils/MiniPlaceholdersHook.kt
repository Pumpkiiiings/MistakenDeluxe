package liric.mistaken.visual.utils

import io.github.miniplaceholders.api.MiniPlaceholders
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.entity.Player

object MiniPlaceholdersHook {
    fun getGlobalPlaceholders(): TagResolver {
        return try {
            val getGlobalMethod = MiniPlaceholders::class.java.getMethod("getGlobalPlaceholders")
            getGlobalMethod.invoke(null) as TagResolver
        } catch (e: Exception) {
            val getGlobalMethod = MiniPlaceholders::class.java.getMethod("globalPlaceholders")
            getGlobalMethod.invoke(null) as TagResolver
        }
    }

    fun getAudiencePlaceholders(player: Player): TagResolver {
        return try {
            val getAudienceMethod = MiniPlaceholders::class.java.getMethod("getAudienceGlobalPlaceholders", Player::class.java)
            getAudienceMethod.invoke(null, player) as TagResolver
        } catch (e: Exception) {
            try {
                val getAudienceMethod = MiniPlaceholders::class.java.getMethod("getAudiencePlaceholders", Player::class.java)
                getAudienceMethod.invoke(null, player) as TagResolver
            } catch (e2: Exception) {
                val getAudienceMethod = MiniPlaceholders::class.java.getMethod("getAudienceGlobalPlaceholders", net.kyori.adventure.audience.Audience::class.java)
                getAudienceMethod.invoke(null, player) as TagResolver
            }
        }
    }
}
