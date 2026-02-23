package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.FastBoard
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * ScoreboardManager: Motor ultra-optimizado para Paper 1.21.4.
 * FIX: Sincronización con carpetas de idioma y actualización instantánea de vidas.
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
            // Esperar a que el plugin de la luz verde
            while (isActive && !plugin.isReady) delay(500)

            while (isActive) {
                try {
                    // Actualización cíclica (Tiempo, Generadores, etc.)
                    updateAll()
                } catch (e: Exception) {
                    // Evitar spam si un archivo YAML está mal escrito
                }
                delay(500L) // 10 Ticks
            }
        }
    }

    /**
     * 🔥 ACTUALIZACIÓN INSTANTÁNEA:
     * Se llama desde CombatManager al cambiar la salud.
     */
    fun updatePlayer(player: Player) {
        val board = boards[player.uniqueId] ?: return
        if (!player.isOnline || !plugin.isReady) return

        // Renderizado asíncrono para no tocar el TPS del server
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

        // 1. RECOLECCIÓN DE DATOS (Frescos de la RAM)
        val onlineCount = Bukkit.getOnlinePlayers().size
        val lives = gm.combatManager.getHealth(player).toString()

        // Formateo de tiempo mm:ss
        val timeStr = if (gm.timer >= 60) {
            String.format("%02d:%02d", gm.timer / 60, gm.timer % 60)
        } else {
            "${gm.timer}s"
        }

        val mapName = gm.currentMapName ?: "---"
        val completed = plugin.generatorManager.getCompletedCount().toString()
        val total = plugin.generatorManager.getTotalGenerators().toString()

        val state = gm.currentState
        val mode = gm.currentMode

        // 2. 🔥 CARGA DE CONFIGURACIÓN (Sistema Pro+ de Carpetas)
        val langCode = plugin.playerDataManager.getLanguage(uuid) ?: "es"
        // Buscamos el archivo principal (messages.yml) dentro de la carpeta (langs/es/)
        val config = plugin.messageConfig.getSpecificFile(player, langCode)

        // Determinar qué sección del scoreboard mostrar
        val stateKey = if (state == GameState.INGAME) {
            "ingame_${mode.name.lowercase()}"
        } else {
            state.name.lowercase()
        }

        val path = "scoreboard.$stateKey"

        // 3. ACTUALIZAR TÍTULO (Legacy para FastBoard)
        val rawTitle = config.getString("scoreboard.title") ?: "<bold>MISTAKEN"
        board.updateTitle(legacy.serialize(mm.deserialize(rawTitle)))

        // 4. PROCESAR LÍNEAS
        val rawLines = config.getStringList(path)
        val processedLines = mutableListOf<String>()

        // Cache de lista de asesinos para no repetir el proceso
        val killerLines = if (rawLines.any { it.contains("%killers%") }) getKillerDisplayStrings(gm) else emptyList()

        for (line in rawLines) {
            if (line.contains("%killers%")) {
                processedLines.addAll(killerLines)
                continue
            }

            // Reemplazo de placeholders ultra-rápido
            val formatted = line
                .replace("%player%", player.name)
                .replace("%timer%", timeStr)
                .replace("%map%", mapName)
                .replace("%online%", onlineCount.toString())
                .replace("%completed%", completed)
                .replace("%total%", total)
                .replace("%lives%", lives) // <--- REEMPLAZO DE VIDAS

            // Deserialización final
            processedLines.add(legacy.serialize(mm.deserialize(formatted)))
        }

        // 5. ENVIAR PAQUETES
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

    // --- GESTIÓN DE JUGADORES ---

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
