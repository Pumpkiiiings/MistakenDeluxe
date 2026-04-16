package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.fastboard.FastBoard
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ScoreboardManager(private val plugin: Mistaken) {

    private val boards = ConcurrentHashMap<UUID, FastBoard>()
    private val mm = plugin.mm
    private val legacy = LegacyComponentSerializer.legacySection()

    init {
        startUpdateTask()
    }

    private fun startUpdateTask() {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate
            try {
                updateAll()
            } catch (e: Exception) {
                // Silencioso
            }
        }, 0L, 500L, TimeUnit.MILLISECONDS)
    }

    fun updatePlayer(player: Player) {
        val board = boards[player.uniqueId] ?: return
        if (!player.isOnline || !plugin.isReady) return

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            val gm = plugin.sessionManager?.getSession(player)
            processAndRender(player, board, gm)
        }
    }

    private fun updateAll() {
        boards.forEach { (uuid, board) ->
            val player = plugin.server.getPlayer(uuid)
            if (player != null && player.isOnline) {
                val gm = plugin.sessionManager?.getSession(player)
                processAndRender(player, board, gm)
            } else {
                boards.remove(uuid)
                board.delete()
            }
        }
    }

    private fun processAndRender(player: Player, board: FastBoard, gm: GameSession?) {
        val onlineCount = plugin.server.onlinePlayers.size.toString()

        if (gm == null) {
            val path = "scoreboard.lobby"
            renderBoard(player, board, null, path, onlineCount, "00:00", "Lobby", "0", "0", emptyList())
            return
        }

        val timeStr = formatTime(gm.timer)
        val mapName = gm.currentMapName

        // Llamadas seguras porque generatorManager puede ser null
        val completed = plugin.generatorManager?.getCompletedCountInWorld(player.world)?.toString() ?: "0"
        val total = plugin.generatorManager?.getTotalGeneratorsInWorld(player.world)?.toString() ?: "0"

        val stateKey = if (gm.currentState == GameState.INGAME)
            "ingame_${gm.currentMode.name.lowercase()}"
        else
            gm.currentState.name.lowercase()

        val path = "scoreboard.$stateKey"
        val killerLines = getKillerDisplayStrings(gm)

        renderBoard(player, board, gm, path, onlineCount, timeStr, mapName, completed, total, killerLines)
    }

    private fun renderBoard(
        player: Player, board: FastBoard, gm: GameSession?, path: String,
        onlineCount: String, timeStr: String, mapName: String,
        completed: String, total: String, killerLines: List<String>
    ) {
        val config = plugin.messageConfig.getSpecificFile(player, "messages")

        val rawTitle = config.getString("scoreboard.title") ?: "<gradient:#88C6F2:#4386B5><bold>MISTAKEN"
        board.updateTitle(legacy.serialize(mm.deserialize(rawTitle)))

        val rawLines = config.getStringList(path)
        if (rawLines.isEmpty()) {
            board.updateLines(listOf("§cError: $path", "§cvacío en YAML"))
            return
        }

        val processedLines = mutableListOf<String>()
        val lives = plugin.combatManager?.getHealth(player)?.toString() ?: "20"
        val sessionID = gm?.sessionId ?: "LOBBY"

        for (line in rawLines) {
            if (line.contains("%killers%")) {
                processedLines.addAll(killerLines)
                continue
            }

            val formatted = line
                .replace("%player%", player.name)
                .replace("%timer%", timeStr)
                .replace("%map%", mapName)
                .replace("%online%", onlineCount)
                .replace("%completed%", completed)
                .replace("%total%", total)
                .replace("%lives%", lives)
                .replace("%id%", sessionID)
                .replace("{", "<").replace("}", ">")

            processedLines.add(legacy.serialize(mm.deserialize(formatted)))
        }

        board.updateLines(processedLines)
    }

    private fun getKillerDisplayStrings(gm: GameSession): List<String> {
        val ids = gm.asesinosUUIDs
        if (ids.isEmpty()) return listOf(legacy.serialize(mm.deserialize(" <gray>Ninguno")))

        val lines = mutableListOf<String>()
        for (id in ids) {
            val killer = plugin.server.getPlayer(id)
            if (killer != null && killer.isOnline) {
                lines.add(legacy.serialize(mm.deserialize(" <white>• <red>${killer.name}")))
            }
        }
        return lines
    }

    private fun formatTime(seconds: Int): String {
        return if (seconds >= 60) String.format("%02d:%02d", seconds / 60, seconds % 60) else seconds.toString()
    }

    fun addPlayer(player: Player) {
        val uuid = player.uniqueId
        boards[uuid]?.let { if (!it.isDeleted) it.delete() }
        boards[uuid] = FastBoard(player)
    }

    fun removePlayer(player: Player) {
        boards.remove(player.uniqueId)?.let { if (!it.isDeleted) it.delete() }
    }

    fun removeAll() {
        boards.values.forEach { if (!it.isDeleted) it.delete() }
        boards.clear()
    }
}
