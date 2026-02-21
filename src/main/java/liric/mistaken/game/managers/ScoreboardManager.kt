package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.FastBoard
import liric.mistaken.utils.mainThread
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * ScoreboardManager: Motor ultra-optimizado.
 * FIX: Se corrigió la lectura de vidas y se optimizó el refresco visual.
 */
class ScoreboardManager(private val plugin: Mistaken) {

    private val boards = ConcurrentHashMap<UUID, FastBoard>()
    private val mm = MiniMessage.miniMessage()
    private val legacy = LegacyComponentSerializer.legacySection()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    init {
        startUpdateTask()
    }

    private fun startUpdateTask() {
        updateJob = scope.launch {
            while (isActive && !plugin.isReady) delay(500)

            while (isActive) {
                try {
                    // Actualización masiva (Cada 10 ticks)
                    updateAll()
                } catch (e: Exception) {
                    // Silencioso para evitar spam en consola
                }
                delay(500L)
            }
        }
    }

    /**
     * 🔥 FIX CRÍTICO: Actualización instantánea.
     * Esta función ahora es mucho más agresiva para asegurar que el cambio se vea.
     */
    fun updatePlayer(player: Player) {
        val board = boards[player.uniqueId] ?: return
        if (!player.isOnline || !plugin.isReady) return

        // Procesamos el renderizado fuera del hilo principal para no laguear
        scope.launch {
            renderBoard(player, board)
        }
    }

    private fun updateAll() {
        boards.forEach { (uuid, board) ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            if (player.isOnline) {
                renderBoard(player, board)
            }
        }
    }

    private fun renderBoard(player: Player, board: FastBoard) {
        val gm = plugin.gameManager
        val uuid = player.uniqueId

        // 1. RECOLECCIÓN DE DATOS (Instantánea)
        val onlineCount = Bukkit.getOnlinePlayers().size
        val lives = gm.combatManager.getHealth(player).toString() // Leemos la vida actualizada
        val timeStr = String.format("%02d:%02d", gm.timer / 60, gm.timer % 60)
        val mapName = gm.currentMapName ?: "---"
        val completed = plugin.generatorManager.getCompletedCount().toString()
        val total = plugin.generatorManager.getTotalGenerators().toString()

        val state = gm.currentState
        val mode = gm.currentMode

        // 2. DETERMINAR RUTA DE CONFIG
        val stateKey = if (state == GameState.INGAME) "ingame_${mode.name.lowercase()}" else state.name.lowercase()
        val path = "scoreboard.$stateKey"

        val lang = plugin.playerDataManager.getLanguage(uuid) ?: "es"
        val config = plugin.messageConfig.getLangConfig(lang)

        // 3. ACTUALIZAR TÍTULO
        val rawTitle = config.getString("scoreboard.title") ?: "<bold>MISTAKEN"
        board.updateTitle(legacy.serialize(mm.deserialize(rawTitle)))

        // 4. PROCESAR LÍNEAS (Optimizado)
        val rawLines = config.getStringList(path)
        val processedLines = mutableListOf<String>()

        // Cache de asesinos para no repetir el proceso por cada línea
        val killerLines = if (rawLines.any { it.contains("%killers%") }) getKillerDisplayStrings(gm) else emptyList()

        for (line in rawLines) {
            if (line.contains("%killers%")) {
                processedLines.addAll(killerLines)
                continue
            }

            // Reemplazo en cadena (Kotlin es más rápido aquí)
            val formatted = line
                .replace("%player%", player.name)
                .replace("%timer%", timeStr)
                .replace("%map%", mapName)
                .replace("%online%", onlineCount.toString())
                .replace("%completed%", completed)
                .replace("%total%", total)
                .replace("%lives%", lives) // <--- REEMPLAZO DE VIDAS

            // Deserializamos y convertimos a Legacy para FastBoard
            processedLines.add(legacy.serialize(mm.deserialize(formatted)))
        }

        // 5. ENVIAR AL JUGADOR
        // FastBoard se encarga de enviar solo los paquetes de las líneas que cambiaron
        board.updateLines(processedLines)
    }

    private fun getKillerDisplayStrings(gm: GameManager): List<String> {
        val lines = mutableListOf<String>()
        val ids = gm.asesinosUUIDs

        if (ids.isEmpty()) {
            lines.add(legacy.serialize(mm.deserialize(" <red>Buscando...")))
            return lines
        }

        for (id in ids) {
            val killer = Bukkit.getPlayer(id)
            if (killer != null && killer.isOnline) {
                lines.add(legacy.serialize(mm.deserialize(" <red>• ${killer.name}")))
            }
        }
        return lines
    }

    // --- MÉTODOS DE CONTROL ---

    fun addPlayer(player: Player) {
        val uuid = player.uniqueId
        boards[uuid]?.let { if (!it.isDeleted) it.delete() }
        boards[uuid] = FastBoard(player)
    }

    fun removePlayer(player: Player) = removePlayer(player.uniqueId)

    fun removePlayer(uuid: UUID) {
        boards.remove(uuid)?.let { if (!it.isDeleted) it.delete() }
    }

    fun removeAll() {
        updateJob?.cancel()
        boards.values.forEach { if (!it.isDeleted) it.delete() }
        boards.clear()
        scope.cancel()
    }
}
