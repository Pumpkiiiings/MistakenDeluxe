package pumpking.lib.scoreboard

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator

/**
 * PacketEvents-backed scoreboard renderer.
 * Bypasses Bukkit entirely for updates, sending packets directly.
 * Uses zero-allocation techniques and batching.
 */
class PacketEventsRenderer : IScoreboardRenderer {

    override val supportsAnimations: Boolean = true
    override val supportsAdvancedRendering: Boolean = true

    override fun render(player: Player, context: ScoreboardContext, template: ScoreboardTemplate) {
        val scoreboard = context.scoreboard
        val maxLines = 15
        val currentLines = template.lines
        var packetsGenerated = 0

        // --- Initialization (Runs once via Bukkit for compatibility) ---
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

        // --- Layout Updates (Bukkit scores) ---
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
            if (template.animatedTitle || context.titleCache != titleText) {
                
                val titleComponent = if (template.animatedTitle) {
                    if (context.strippedTitleCache == null || context.titleCache != titleText) {
                        context.strippedTitleCache = stripColors(titleText)
                    }
                    ScoreboardAnimator.animatedGradient(context.strippedTitleCache!!, context.animTick)
                } else {
                    ColorTranslator.translate(titleText)
                }

                context.objective.displayName(titleComponent)
                packetsGenerated++

                context.titleCache = titleText
            }
            context.titleChanged = false
        }

        // --- Line Updates ---
        for (i in 0 until currentLines.size) {
            if (!context.lineChanged[i]) continue

            var componentToApply: Component? = null

            if (!template.isLineDynamic[i]) {
                componentToApply = context.staticLineCache[i]
            } else {
                val rawLine = currentLines[i].replace("%player%", player.name)
                val isAnimated = rawLine.contains("<anim>")
                
                if (isAnimated) {
                    if (context.strippedLineCache[i] == null || context.lineCache[i] != rawLine) {
                        context.strippedLineCache[i] = rawLine.replace("<anim>", "").replace("</anim>", "").trim()
                    }
                    componentToApply = ScoreboardAnimator.animatedGradient(context.strippedLineCache[i]!!, context.animTick + i * 15)
                    context.lineCache[i] = rawLine
                } else {
                    if (context.lineCache[i] != rawLine) {
                        componentToApply = ColorTranslator.translate(rawLine)
                        context.lineCache[i] = rawLine
                    }
                }
            }

            if (componentToApply != null) {
                sendTeamPrefixPacket(player, i, componentToApply)
                packetsGenerated++
            }
        }

        // Clear unused lines if layout shrunk
        for (i in currentLines.size until maxLines) {
            if (context.lineChanged[i] || context.lineCache[i] != null) {
                sendTeamPrefixPacket(player, i, Component.empty())
                packetsGenerated++
                context.lineCache[i] = null
            }
        }
        
        ScoreboardProfiler.recordPackets(packetsGenerated)
    }

    private fun sendTeamPrefixPacket(player: Player, lineIndex: Int, prefix: Component) {
        val teamName = ScoreboardConstants.TEAM_NAMES[lineIndex]
        val info = WrapperPlayServerTeams.ScoreBoardTeamInfo(
            Component.empty(),
            prefix,
            Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
            WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE,
            WrapperPlayServerTeams.OptionData.NONE
        )
        val packet = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.UPDATE, info)
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    }

    private fun stripColors(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("§[0-9a-fklmnor]"), "")
            .replace(Regex("&#[0-9A-Fa-f]{6}"), "")
            .replace(Regex("#[0-9A-Fa-f]{6}"), "")
            .trim()
    }
}
