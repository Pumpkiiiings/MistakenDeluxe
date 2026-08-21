package liric.mistaken.utils.scoreboard

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import liric.mistaken.utils.color.ColorTranslator

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

        
        if (context.titleChanged) {
            val titleText = template.title.replace("%player%", player.name)
            if (context.titleCache != titleText) {
                val parsedTitle = ColorTranslator.translate(titleText)
                context.objective.displayName(parsedTitle)
                context.titleCache = titleText
            }
            context.titleChanged = false
        }

        
        for (i in 0 until currentLines.size) {
            if (!context.lineChanged[i]) continue

            var componentToApply: Component? = null

            if (!template.isLineDynamic[i]) {
                
                componentToApply = context.staticLineCache[i]
            } else {
                
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
        
        
        for (i in currentLines.size until maxLines) {
            if (context.lineChanged[i] || context.lineCache[i] != null) {
                val team = scoreboard.getTeam(ScoreboardConstants.TEAM_NAMES[i])
                team?.prefix(Component.empty())
                context.lineCache[i] = null
            }
        }
    }
}
