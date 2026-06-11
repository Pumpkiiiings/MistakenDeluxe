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

data class ScoreboardContext(
    val scoreboard: Scoreboard,
    val objective: Objective,
    var templateId: String? = null
)
