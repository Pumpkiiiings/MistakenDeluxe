package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameManager // Importamos la nueva ubicación
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.FastBoard
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * [LIRIC-MISTAKEN 2.0]
 * ScoreboardManager: Motor Pro+
 * FIX: 100% Asíncrono (Zero-Lag TPS), optimización extrema de CPU y memoria.
 */
class ScoreboardManager(private val plugin: Mistaken) {

    private val boards = ConcurrentHashMap<UUID, FastBoard>()
    private val mm = plugin.mm
    private val legacy = LegacyComponentSerializer.legacySection()

    init {
        startUpdateTask()
    }

    private fun startUpdateTask() {
        // 🔥 FIX: Tarea 100% Asíncrona (Fuera del hilo principal)
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate
            try {
                updateAll()
            } catch (e: Exception) {
                // Silencioso para no saturar consola en reloads
            }
        }, 0L, 500L, TimeUnit.MILLISECONDS) // 500ms = 10 ticks (Media segundo)
    }

    fun updatePlayer(player: Player) {
        val board = boards[player.uniqueId] ?: return
        if (!player.isOnline || !plugin.isReady) return

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            val gm = plugin.gameManager

            // Datos Globales
            val onlineCount = plugin.server.onlinePlayers.size.toString()
            val timeStr = formatTime(gm.timer)
            val mapName = gm.currentMapName
            val completed = plugin.generatorManager.getCompletedCount().toString()
            val total = plugin.generatorManager.getTotalGenerators().toString()

            // Lógica de estado
            val stateKey = if (gm.currentState == GameState.INGAME)
                "ingame_${gm.currentMode.name.lowercase()}"
            else
                gm.currentState.name.lowercase()

            val path = "scoreboard.$stateKey"
            val killerLines = getKillerDisplayStrings(gm)

            renderBoard(player, board, gm, path, onlineCount, timeStr, mapName, completed, total, killerLines)
        }
    }

    private fun updateAll() {
        val gm = plugin.gameManager

        // 🔥 OPTIMIZACIÓN: Pre-calculamos datos globales UNA SOLA VEZ
        val onlineCount = plugin.server.onlinePlayers.size.toString()
        val timeStr = formatTime(gm.timer)
        val mapName = gm.currentMapName
        val completed = plugin.generatorManager.getCompletedCount().toString()
        val total = plugin.generatorManager.getTotalGenerators().toString()

        val stateKey = if (gm.currentState == GameState.INGAME)
            "ingame_${gm.currentMode.name.lowercase()}"
        else
            gm.currentState.name.lowercase()

        val path = "scoreboard.$stateKey"
        val killerLines = getKillerDisplayStrings(gm)

        // Iteramos solo sobre los tableros activos
        boards.forEach { (uuid, board) ->
            val player = plugin.server.getPlayer(uuid)
            if (player != null && player.isOnline) {
                renderBoard(player, board, gm, path, onlineCount, timeStr, mapName, completed, total, killerLines)
            } else {
                // Limpieza de memoria si se desconectó
                boards.remove(uuid)
                board.delete()
            }
        }
    }

    private fun renderBoard(
        player: Player, board: FastBoard, gm: GameManager, path: String,
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
        val lives = plugin.combatManager.getHealth(player).toString() // Usamos plugin.combatManager

        for (line in rawLines) {
            if (line.contains("%killers%")) {
                processedLines.addAll(killerLines)
                continue
            }

            // Reemplazo de variables eficiente
            var formatted = line
                .replace("%player%", player.name)
                .replace("%timer%", timeStr)
                .replace("%map%", mapName)
                .replace("%online%", onlineCount)
                .replace("%completed%", completed)
                .replace("%total%", total)
                .replace("%lives%", lives)
                .replace("{", "<").replace("}", ">") // Soporte legacy para tags < >

            // Procesamos MiniMessage a Legacy (Colores clásicos para Scoreboard)
            processedLines.add(legacy.serialize(mm.deserialize(formatted)))
        }

        board.updateLines(processedLines)
    }

    private fun getKillerDisplayStrings(gm: GameManager): List<String> {
        // 🔥 FIX: Iteración segura sobre ConcurrentHashMap KeySet
        val ids = gm.asesinosUUIDs
        if (ids.isEmpty()) return listOf(legacy.serialize(mm.deserialize(" <gray>Ninguno")))

        val lines = mutableListOf<String>()
        for (id in ids) {
            val killer = plugin.server.getPlayer(id)
            // 🔥 FIX: Chequeo de nulidad y isOnline antes de acceder a .name
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
