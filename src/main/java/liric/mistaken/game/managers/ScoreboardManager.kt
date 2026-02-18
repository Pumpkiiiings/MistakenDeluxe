package liric.mistaken.game.managers

import liric.mistaken.utils.FastBoard
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * ScoreboardManager: Motor de marcadores basado en FastBoard (Packets).
 * Optimización extrema mediante Coroutines y pre-procesamiento de datos.
 */
class ScoreboardManager(private val plugin: Mistaken) {

    private val boards = ConcurrentHashMap<UUID, FastBoard>()
    private val mm = MiniMessage.miniMessage()
    private val legacy = LegacyComponentSerializer.legacySection()

    // Motor de corrutinas para el procesamiento asíncrono
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    private var cachedTitle: String = ""

    init {
        startUpdateTask()
    }

    private fun startUpdateTask() {
        // Sustituimos el Bukkit Scheduler por Coroutines (más eficiente)
        updateJob = scope.launch {
            while (isActive) {
                try {
                    updateAllBoards()
                } catch (e: Exception) {
                    plugin.logger.severe("Error en el ciclo del Scoreboard: ${e.message}")
                }
                delay(1000L) // Actualización cada 1 segundo (20 ticks)
            }
        }
    }

    /**
     * Motor Asíncrono: Pre-calcula datos globales una sola vez por ciclo.
     */
    private fun updateAllBoards() {
        val gm = plugin.gameManager
        val state = gm.currentState
        val mode = gm.currentMode

        // 1. Pre-calculamos datos globales una sola vez (Ahorro crítico de CPU)
        val timeStr = String.format("%02d:%02d", gm.timer / 60, gm.timer % 60)
        val mapName = gm.currentMapName
        val onlineStr = Bukkit.getOnlinePlayers().size.toString()
        val completedStr = plugin.generatorManager.getCompletedCount().toString()
        val totalStr = plugin.generatorManager.getTotalGenerators().toString()
        val killerDisplay = getKillerDisplayStrings(gm)

        val path = "scoreboard.${state.name.lowercase()}${if (state == GameState.INGAME) "_${mode.name.lowercase()}" else ""}"

        // 2. Procesamos cada jugador de forma independiente
        boards.forEach { (uuid, board) ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            if (!player.isOnline) return@forEach

            // Obtener líneas según el idioma del jugador (Componentes -> Legacy String)
            val rawLines = plugin.messageConfig.getLangConfig(plugin.playerDataManager.getLanguage(uuid))
                .getStringList("$path.lines")

            val processedLines = mutableListOf<String>()
            val healthStr = gm.getHealth(player).toString()
            val pName = player.name

            for (line in rawLines) {
                if (line.contains("%killers%")) {
                    processedLines.addAll(killerDisplay)
                    continue
                }

                // Reemplazo de placeholders ultra-rápido
                val finalLine = line
                    .replace("%timer%", timeStr)
                    .replace("%map%", mapName)
                    .replace("%online%", onlineStr)
                    .replace("%completed%", completedStr)
                    .replace("%total%", totalStr)
                    .replace("%lives%", healthStr)
                    .replace("%player%", pName)

                // Convertimos MiniMessage a Legacy (§) para los paquetes de FastBoard
                processedLines.add(legacy.serialize(mm.deserialize(finalLine)))
            }

            // 3. Aplicar actualización al Board
            // Título: Solo se procesa si el cache está vacío o se requiere actualización
            if (cachedTitle.isEmpty()) {
                val rawTitle = plugin.messageConfig.getRawString(player, "scoreboard.title", "<bold>MISTAKEN")
                cachedTitle = legacy.serialize(mm.deserialize(rawTitle))
            }

            // FastBoard envía los paquetes asíncronamente de forma segura
            board.updateTitle(cachedTitle)
            board.updateLines(processedLines)
        }
    }

    private fun getKillerDisplayStrings(gm: GameManager): List<String> {
        val lines = mutableListOf<String>()
        val killerUUIDs = gm.asesinosUUIDs

        if (gm.currentMode == MistakenMode.ONE_BOUNCE) {
            lines.add(legacy.serialize(mm.deserialize("<white>Asesinos: <red>${killerUUIDs.size}")))
        } else {
            killerUUIDs.forEach { uuid ->
                Bukkit.getPlayer(uuid)?.let { killer ->
                    lines.add(legacy.serialize(mm.deserialize(" <red>• ${killer.name}")))
                }
            }
        }
        return lines
    }

    // --- MÉTODOS DE GESTIÓN ---

    fun addPlayer(player: Player) {
        boards[player.uniqueId] = FastBoard(player)
    }

    fun removePlayer(player: Player) {
        boards.remove(player.uniqueId)?.let {
            if (!it.isDeleted) it.delete()
        }
    }

    fun removeAll() {
        updateJob?.cancel()
        boards.values.forEach { if (!it.isDeleted) it.delete() }
        boards.clear()
        scope.cancel()
    }

    fun reload() {
        cachedTitle = ""
    }
}
