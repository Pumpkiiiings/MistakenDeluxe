package liric.mistaken.game.managers.visual

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import liric.mistaken.utils.scoreboard.ScoreboardTemplate
import liric.mistaken.utils.scoreboard.ScoreboardManager as PumpkingScoreboardManager
import java.util.UUID
import liric.mistaken.utils.hooks.ObserverHook
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService

/**
 * [LIRIC-MISTAKEN 2.0]
 * ScoreboardManager: Motor Multiarena / Velocity.
 * Migrated to MistakenLib ScoreboardManager backend (FastBoard removed).
 * Dynamic templates resolve live game state per-player every tick.
 */
class ScoreboardManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val legacy = LegacyComponentSerializer.legacySection()

    fun updatePlayer(player: Player) {
        if (ObserverHook.hasObserver(player)) {
            PumpkingScoreboardManager.removeScoreboard(player)
            return
        }

        val config = MessageService.getSpecificFile(player, "messages")
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

    private fun buildLines(player: Player): List<String> {
        val gm = plugin.sessionManager.getSession(player)
        val config = MessageService.getSpecificFile(player, "messages")
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
            killerLines = getKillerDisplayStrings(gm.killersUUIDs)

            path = if (gm.currentState == GameState.INGAME)
                "scoreboard.ingame_${gm.currentMode.name.lowercase()}"
            else
                "scoreboard.${gm.currentState.name.lowercase()}"
        }

        val rawLines = config.getStringList(path)
        if (rawLines.isEmpty()) {
            return listOf("�cError: $path", "�cvac�o en YAML")
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

            
            result.add(legacy.serialize(ColorTranslator.translate(formatted)))
        }

        return result
    }

    private fun getKillerDisplayStrings(ids: Set<UUID>): List<String> {
        if (ids.isEmpty()) return listOf(legacy.serialize(ColorTranslator.translate(" <gray>Ninguno")))
        return ids.mapNotNull { id ->
            val killer = plugin.server.getPlayer(id)
            if (killer != null && killer.isOnline)
                legacy.serialize(ColorTranslator.translate(" <white>� <red>${killer.name}"))
            else null
        }
    }

    private fun formatTime(seconds: Int): String =
        if (seconds >= 60) String.format("%02d:%02d", seconds / 60, seconds % 60) else seconds.toString()

    

    fun addPlayer(player: Player) {
        PumpkingScoreboardManager.assignScoreboard(player, player.name)
    }

    fun removePlayer(player: Player) {
        PumpkingScoreboardManager.removeScoreboard(player)
    }


    fun removeAll() {
        for (player in plugin.server.onlinePlayers) {
            runCatching { removePlayer(player) }
        }
    }
}
