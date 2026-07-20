package liric.mistaken.game.managers.visual

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import pumpking.lib.scoreboard.ScoreboardTemplate
import pumpking.lib.scoreboard.ScoreboardManager as PumpkingScoreboardManager
import java.util.UUID
import liric.mistaken.utils.hooks.ObserverHook
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

/**
 * [LIRIC-MISTAKEN 2.0]
 * ScoreboardManager: Motor Multiarena / Velocity.
 * Migrated to PumpkingLib ScoreboardManager backend (FastBoard removed).
 * Dynamic templates resolve live game state per-player every tick.
 */
class ScoreboardManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val legacy = LegacyComponentSerializer.legacySection()

    private var updateTask: BukkitTask? = null

    init {
        startUpdateTask()
    }

    private fun startUpdateTask() {
        val interval = if (PumpkingScoreboardManager.supportsAnimations()) 2L else 10L
        updateTask = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            for (player in plugin.server.onlinePlayers) {
                if (ObserverHook.hasObserver(player)) {
                    PumpkingScoreboardManager.removeScoreboard(player)
                    continue
                }

                val config = PumpkingServiceManager.messages.getSpecificFile(player, "messages")
                val title = config.getString("scoreboard.title") ?: "<gradient:#88C6F2:#4386B5><bold>MISTAKEN"
                val lines = buildLines(player)

                val template = ScoreboardTemplate(
                    id = player.name,
                    title = title,
                    lines = lines,
                    animatedTitle = false
                )

                PumpkingScoreboardManager.registerTemplate(template)
            }
        }, 20L, interval)
    }

    private fun buildLines(player: Player): List<String> {
        val gm = plugin.sessionManager.getSession(player)
        val config = PumpkingServiceManager.messages.getSpecificFile(player, "messages")
        val onlineCount = plugin.server.onlinePlayers.size.toString()

        val path: String
        val timeStr: String
        val mapName: String
        val completed: String
        val total: String
        val killerLines: List<String>

        if (gm == null) {
            path = "scoreboard.lobby"
            timeStr = "00:00"
            mapName = "Lobby"
            completed = "0"
            total = "0"
            killerLines = emptyList()
        } else {
            timeStr = formatTime(gm.timer)
            mapName = gm.currentMapName
            completed = plugin.generatorManager.getCompletedCountInWorld(player.world).toString()
            total = plugin.generatorManager.getTotalGeneratorsInWorld(player.world).toString()
            killerLines = getKillerDisplayStrings(gm.asesinosUUIDs)

            path = if (gm.currentState == GameState.INGAME)
                "scoreboard.ingame_${gm.currentMode.name.lowercase()}"
            else
                "scoreboard.${gm.currentState.name.lowercase()}"
        }

        val rawLines = config.getStringList(path)
        if (rawLines.isEmpty()) {
            return listOf("§cError: $path", "§cvacío en YAML")
        }

        val lives = plugin.combatManager.getHealth(player).toString()
        val sessionID = gm?.id ?: "LOBBY"
        val result = mutableListOf<String>()

        for (line in rawLines) {
            if (line.contains("%killers%")) {
                result.addAll(killerLines)
                continue
            }
            var formatted = line
                .replace("%player%", player.name)
                .replace("%timer%", timeStr)
                .replace("%map%", mapName)
                .replace("%online%", onlineCount)
                .replace("%completed%", completed)
                .replace("%total%", total)
                .replace("%lives%", lives)
                .replace("%id%", sessionID)
                .replace("{", "<").replace("}", ">")

            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                formatted = PlaceholderAPI.setPlaceholders(player, formatted)
            }

            // Convert to legacy string so the existing YAML format (MiniMessage) is preserved
            result.add(legacy.serialize(ColorTranslator.translate(formatted)))
        }

        return result
    }

    private fun getKillerDisplayStrings(ids: Set<UUID>): List<String> {
        if (ids.isEmpty()) return listOf(legacy.serialize(ColorTranslator.translate(" <gray>Ninguno")))
        return ids.mapNotNull { id ->
            val killer = plugin.server.getPlayer(id)
            if (killer != null && killer.isOnline)
                legacy.serialize(ColorTranslator.translate(" <white>• <red>${killer.name}"))
            else null
        }
    }

    private fun formatTime(seconds: Int): String =
        if (seconds >= 60) String.format("%02d:%02d", seconds / 60, seconds % 60) else seconds.toString()

    // --- Public API (same contract as before) ---

    fun addPlayer(player: Player) {
        PumpkingScoreboardManager.assignScoreboard(player, player.name)
    }

    fun removePlayer(player: Player) {
        PumpkingScoreboardManager.removeScoreboard(player)
    }

    /** Called when game state changes to force a re-render on the next tick. */
    fun updatePlayer(player: Player) {
        // PumpkingLib updates on every tick automatically.
        // This call is kept for API compatibility but nothing additional needs to happen.
    }

    fun removeAll() {
        for (player in plugin.server.onlinePlayers) {
            runCatching { removePlayer(player) }
        }
    }
}

