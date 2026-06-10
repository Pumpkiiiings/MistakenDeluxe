package pumpking.lib.scoreboard

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Base contract for all scoreboard renderer backends.
 * Both Bukkit and PacketEvents renderers must implement this.
 */
interface IScoreboardRenderer {

    /**
     * Render the current frame of a template for a specific player.
     * Implementors must perform dirty checking internally to avoid unnecessary updates.
     */
    fun render(player: Player, context: ScoreboardContext, template: ScoreboardTemplate)

    /**
     * Clear all cached state for a specific player.
     * Call when removing a scoreboard or assigning a new template.
     */
    fun clearCache(uuid: UUID)

    /**
     * Whether this renderer supports animated RGB gradients and animated titles.
     */
    val supportsAnimations: Boolean

    /**
     * Whether this renderer supports packet-level rendering optimizations.
     */
    val supportsAdvancedRendering: Boolean
}
