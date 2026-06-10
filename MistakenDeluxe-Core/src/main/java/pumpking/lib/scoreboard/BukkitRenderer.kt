package pumpking.lib.scoreboard

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bukkit-native scoreboard renderer.
 * Uses Team-based sidebar rendering with Adventure API Components.
 * Performs dirty checking via serialized GsonComponent strings to avoid redundant updates.
 *
 * No PacketEvents dependency. Always available.
 */
class BukkitRenderer : IScoreboardRenderer {

    override val supportsAnimations: Boolean = false
    override val supportsAdvancedRendering: Boolean = false

    // Cache: UUID -> { lineIndex -> serialized Component JSON }
    // -1 is used as index for the title
    private val lineCache = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, String>>()

    override fun render(player: Player, context: ScoreboardContext, template: ScoreboardTemplate) {
        val uuid = player.uniqueId
        val cache = lineCache.getOrPut(uuid) { ConcurrentHashMap() }
        val scoreboard = context.scoreboard
        val maxLines = 15
        val currentLines = template.lines

        // --- Title ---
        val parsedTitle = ColorTranslator.translate(template.title)
        val serializedTitle = GsonComponentSerializer.gson().serialize(parsedTitle)
        if (cache[-1] != serializedTitle) {
            context.objective.displayName(parsedTitle)
            cache[-1] = serializedTitle
        }

        // --- Scores & Teams setup ---
        for (i in 0 until maxLines) {
            val teamName = "sb_line_${String.format("%02d", i)}"
            val entryName = entryForLine(i)
            var team = scoreboard.getTeam(teamName)

            if (team == null) {
                team = scoreboard.registerNewTeam(teamName)
                team.addEntry(entryName)
            }

            val scoreObj = context.objective.getScore(entryName)
            if (i < currentLines.size) {
                if (scoreObj.score != maxLines - i) scoreObj.score = maxLines - i
            } else {
                if (scoreboard.entries.contains(entryName) && scoreObj.isScoreSet) {
                    scoreboard.resetScores(entryName)
                }
            }
        }

        // --- Line content ---
        for (i in 0 until maxLines) {
            val teamName = "sb_line_${String.format("%02d", i)}"
            val team = scoreboard.getTeam(teamName) ?: continue

            if (i < currentLines.size) {
                val processed = currentLines[i].replace("%player%", player.name)
                val component = ColorTranslator.translate(processed)
                val serialized = GsonComponentSerializer.gson().serialize(component)

                if (cache[i] != serialized) {
                    team.prefix(component)
                    cache[i] = serialized
                }
            } else {
                if (cache.containsKey(i)) {
                    team.prefix(Component.empty())
                    cache.remove(i)
                }
            }
        }
    }

    override fun clearCache(uuid: UUID) {
        lineCache.remove(uuid)
    }

    private fun entryForLine(line: Int): String {
        val codes = arrayOf("§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f")
        return codes[line] + "§r"
    }
}
