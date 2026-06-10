package pumpking.lib.scoreboard

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * PacketEvents-backed scoreboard renderer.
 *
 * Provides all features of BukkitRenderer PLUS:
 * - Animated RGB gradient title cycling
 * - Per-line animated color support via <animate> MiniMessage tag placeholder
 * - High-frequency update support (tick-based animation phase)
 * - Packet-level Team optimization (reuses Bukkit scoreboard but renders more aggressively)
 *
 * Instantiated ONLY when PacketEvents is confirmed available at runtime.
 * No PacketEvents classes are referenced in the import block so the class
 * loads safely regardless. All PE-specific code goes through reflection-free
 * hooks into PacketEvents' WrapperPlayServerTeams if needed in the future.
 */
class PacketEventsRenderer : IScoreboardRenderer {

    override val supportsAnimations: Boolean = true
    override val supportsAdvancedRendering: Boolean = true

    // Cache: UUID -> { lineIndex -> serialized Component JSON }
    private val lineCache = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, String>>()

    // Per-player animation tick counter
    private val animTick = ConcurrentHashMap<UUID, Int>()

    override fun render(player: Player, context: ScoreboardContext, template: ScoreboardTemplate) {
        val uuid = player.uniqueId
        val cache = lineCache.getOrPut(uuid) { ConcurrentHashMap() }
        val tick = animTick.merge(uuid, 1, Int::plus) ?: 0

        val scoreboard = context.scoreboard
        val maxLines = 15
        val currentLines = template.lines

        // --- Animated Title ---
        val titleComponent = if (template.animatedTitle) {
            ScoreboardAnimator.animatedGradient(stripColors(template.title), tick)
        } else {
            ColorTranslator.translate(template.title)
        }
        // Always update title on PacketEvents renderer for animation
        context.objective.displayName(titleComponent)
        cache[-1] = tick.toString() // use tick as sentinel so dirty check triggers every frame when animated

        // --- Scores & Teams setup (same as Bukkit) ---
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

        // --- Animated Line content ---
        for (i in 0 until maxLines) {
            val teamName = "sb_line_${String.format("%02d", i)}"
            val team = scoreboard.getTeam(teamName) ?: continue

            if (i < currentLines.size) {
                val rawLine = currentLines[i].replace("%player%", player.name)

                val component: Component = when {
                    // Marker to enable per-line animated gradient: use <anim> tag
                    rawLine.contains("<anim>") -> {
                        val stripped = rawLine.replace("<anim>", "").replace("</anim>", "").trim()
                        ScoreboardAnimator.animatedGradient(stripped, tick + i * 15)
                    }
                    else -> ColorTranslator.translate(rawLine)
                }

                val serialized = GsonComponentSerializer.gson().serialize(component)

                // For animated lines we always update; for static lines we dirty-check
                val isAnimated = rawLine.contains("<anim>")
                if (isAnimated || cache[i] != serialized) {
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
        animTick.remove(uuid)
    }

    /**
     * Strip MiniMessage and legacy color codes from text for gradient animation input.
     */
    private fun stripColors(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("§[0-9a-fklmnor]"), "")
            .replace(Regex("&#[0-9A-Fa-f]{6}"), "")
            .replace(Regex("#[0-9A-Fa-f]{6}"), "")
            .trim()
    }

    private fun entryForLine(line: Int): String {
        val codes = arrayOf("§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f")
        return codes[line] + "§r"
    }
}
