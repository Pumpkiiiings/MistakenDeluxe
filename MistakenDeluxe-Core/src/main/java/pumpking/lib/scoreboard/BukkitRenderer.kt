package pumpking.lib.scoreboard

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator

/**
 * Bukkit-native scoreboard renderer.
 * Zero-allocation inside the render loop.
 * Respects dirty flags to avoid redundant updates.
 */
class BukkitRenderer : IScoreboardRenderer {

    override val supportsAnimations: Boolean = false
    override val supportsAdvancedRendering: Boolean = false

    override fun render(player: Player, context: ScoreboardContext, template: ScoreboardTemplate) {
        val scoreboard = context.scoreboard
        val maxLines = 15
        val currentLines = template.lines

        // --- Initialization (Runs once) ---
        if (!context.initialized) {
            for (i in 0 until maxLines) {
                val teamName = ScoreboardConstants.TEAM_NAMES[i]
                val entryName = ScoreboardConstants.ENTRY_NAMES[i]
                var team = scoreboard.getTeam(teamName)

                if (team == null) {
                    team = scoreboard.registerNewTeam(teamName)
                    team.addEntry(entryName)
                }
            }
            context.initialized = true
        }

        // --- Layout Updates (Runs only when line count changes) ---
        if (context.layoutChanged || currentLines.size != context.activeLines) {
            for (i in 0 until maxLines) {
                val entryName = ScoreboardConstants.ENTRY_NAMES[i]
                val scoreObj = context.objective.getScore(entryName)

                if (i < currentLines.size) {
                    val targetScore = maxLines - i
                    if (scoreObj.score != targetScore) {
                        scoreObj.score = targetScore
                    }
                } else if (i < context.activeLines) {
                    scoreboard.resetScores(entryName)
                }
            }
            context.activeLines = currentLines.size
            context.layoutChanged = false
        }

        // --- Title Updates ---
        if (context.titleChanged) {
            val titleText = template.title.replace("%player%", player.name)
            if (context.titleCache != titleText) {
                val parsedTitle = ColorTranslator.translate(titleText)
                context.objective.displayName(parsedTitle)
                context.titleCache = titleText
            }
            context.titleChanged = false
        }

        // --- Line Updates ---
        for (i in 0 until currentLines.size) {
            if (!context.lineChanged[i]) continue

            var componentToApply: Component? = null

            if (!template.isLineDynamic[i]) {
                // Completely static line
                componentToApply = context.staticLineCache[i]
            } else {
                // Dynamic line (only %player% supported in BukkitRenderer for now)
                val rawLine = currentLines[i].replace("%player%", player.name)
                if (context.lineCache[i] != rawLine) {
                    componentToApply = ColorTranslator.translate(rawLine)
                    context.lineCache[i] = rawLine
                }
            }

            if (componentToApply != null) {
                val team = scoreboard.getTeam(ScoreboardConstants.TEAM_NAMES[i])
                team?.prefix(componentToApply)
            }
        }
        
        // Clear unused lines if layout shrunk
        for (i in currentLines.size until maxLines) {
            if (context.lineChanged[i] || context.lineCache[i] != null) {
                val team = scoreboard.getTeam(ScoreboardConstants.TEAM_NAMES[i])
                team?.prefix(Component.empty())
                context.lineCache[i] = null
            }
        }
    }
}
