package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.FastBoard
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 *[LIRIC-MISTAKEN 2.0]
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
        // 🔥 FIX 1: Tarea 100% Asíncrona (Fuera del hilo principal)
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate

            try {
                updateAll()
            } catch (e: Exception) {
                // Silencioso para no saturar consola
            }
        }, 0L, 500L, TimeUnit.MILLISECONDS) // 500ms = 10 ticks
    }

    fun updatePlayer(player: Player) {
        val board = boards[player.uniqueId] ?: return
        if (!player.isOnline || !plugin.isReady) return

        // También lo mandamos al hilo asíncrono para mantener el rendimiento
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            val gm = plugin.gameManager

            // Calculamos rápidamente las variables solo para este jugador
            val onlineCount = plugin.server.onlinePlayers.size.toString()
            val timeStr = if (gm.timer >= 60) String.format("%02d:%02d", gm.timer / 60, gm.timer % 60) else gm.timer.toString()
            val mapName = gm.currentMapName
            val completed = plugin.generatorManager.getCompletedCount().toString()
            val total = plugin.generatorManager.getTotalGenerators().toString()
            val stateKey = if (gm.currentState == GameState.INGAME) "ingame_${gm.currentMode.name.lowercase()}" else gm.currentState.name.lowercase()
            val path = "scoreboard.$stateKey"
            val killerLines = getKillerDisplayStrings(gm)

            // CORRECCIÓN: Le pasamos todos los argumentos que necesita
            renderBoard(player, board, gm, path, onlineCount, timeStr, mapName, completed, total, killerLines)
        }
    }

    private fun updateAll() {
        val gm = plugin.gameManager

        // 🔥 FIX 2: Pre-calculamos datos globales UNA SOLA VEZ, no 20 veces por cada jugador.
        val onlineCount = plugin.server.onlinePlayers.size.toString()
        val timeStr = if (gm.timer >= 60) String.format("%02d:%02d", gm.timer / 60, gm.timer % 60) else gm.timer.toString()
        val mapName = gm.currentMapName
        val completed = plugin.generatorManager.getCompletedCount().toString()
        val total = plugin.generatorManager.getTotalGenerators().toString()
        val stateKey = if (gm.currentState == GameState.INGAME) "ingame_${gm.currentMode.name.lowercase()}" else gm.currentState.name.lowercase()
        val path = "scoreboard.$stateKey"

        val killerLines = getKillerDisplayStrings(gm)

        boards.forEach { (uuid, board) ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null && player.isOnline) {
                renderBoard(player, board, gm, path, onlineCount, timeStr, mapName, completed, total, killerLines)
            } else {
                // Si el jugador se desconectó y quedó colgado, lo borramos de RAM
                board.delete()
                boards.remove(uuid)
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
        val lives = gm.combatManager.getHealth(player).toString()

        for (line in rawLines) {
            if (line.contains("%killers%")) {
                processedLines.addAll(killerLines)
                continue
            }

            // 🔥 FIX 3: Solo parseamos MiniMessage, es rapidísimo en memoria pura
            val formatted = line
                .replace("%player%", player.name)
                .replace("%timer%", timeStr)
                .replace("%map%", mapName)
                .replace("%online%", onlineCount)
                .replace("%completed%", completed)
                .replace("%total%", total)
                .replace("%lives%", lives)
                .replace("{", "<").replace("}", ">")

            processedLines.add(legacy.serialize(mm.deserialize(formatted)))
        }

        board.updateLines(processedLines)
    }

    private fun getKillerDisplayStrings(gm: GameManager): List<String> {
        val ids = gm.asesinosUUIDs
        if (ids.isEmpty()) return listOf(legacy.serialize(mm.deserialize(" <gray>Ninguno")))

        val lines = mutableListOf<String>()
        for (id in ids) {
            val killer = Bukkit.getPlayer(id)
            if (killer != null && killer.isOnline) {
                lines.add(legacy.serialize(mm.deserialize(" <white>• <red>${killer.name}")))
            }
        }
        return lines
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
