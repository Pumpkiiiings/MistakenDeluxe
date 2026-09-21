package liric.mistaken.visual.utils

import io.github.miniplaceholders.api.MiniPlaceholders
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.entity.Player

object MiniPlaceholdersHook {
    fun getGlobalPlaceholders(): TagResolver {
        return MiniPlaceholders.getGlobalPlaceholders()
    }

    fun getAudiencePlaceholders(player: Player): TagResolver {
        return MiniPlaceholders.getAudiencePlaceholders(player)
    }
}
