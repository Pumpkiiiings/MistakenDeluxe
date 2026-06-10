package pumpking.lib.scoreboard

import org.bukkit.entity.Player
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard

/**
 * Static template: title and lines are fixed strings (may contain placeholders like %player%).
 */
data class ScoreboardTemplate(
    val id: String,
    val title: String,
    val lines: List<String>,
    val animatedTitle: Boolean = false
)

/**
 * Dynamic template: title and lines are resolved at render time per player via lambdas.
 * Use this when scoreboard content depends on live game state (timers, session data, etc.).
 */
data class DynamicScoreboardTemplate(
    val id: String,
    val titleSupplier: (Player) -> String,
    val linesSupplier: (Player) -> List<String>,
    val animatedTitle: Boolean = false
)

data class ScoreboardContext(
    val scoreboard: Scoreboard,
    val objective: Objective,
    var templateId: String? = null,
    var dynamicTemplateId: String? = null
)
