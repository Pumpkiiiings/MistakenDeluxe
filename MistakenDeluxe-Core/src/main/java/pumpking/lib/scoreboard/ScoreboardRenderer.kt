package pumpking.lib.scoreboard

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Compatibility shim. Delegates all calls to whichever renderer
 * is currently active in ScoreboardManager.
 *
 * Kept so that any external call sites using the old `ScoreboardRenderer`
 * reference continue to compile without modification.
 */
@Deprecated(
    "Use ScoreboardManager.getRenderer() directly. This shim will be removed in a future release.",
    ReplaceWith("ScoreboardManager.getRenderer()")
)
object ScoreboardRenderer {

    fun render(player: Player, context: ScoreboardContext, template: ScoreboardTemplate) {
        ScoreboardManager.getRenderer().render(player, context, template)
    }

    fun clearCache(uuid: UUID) {
        ScoreboardManager.getRenderer().clearCache(uuid)
    }
}
