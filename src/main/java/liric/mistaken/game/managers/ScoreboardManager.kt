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
 * ScoreboardManager: Motor Pro+ adaptado a tu estructura YAML.
 * FIX: Ruta de llaves corregida (scoreboard.<estado>).
 */
class ScoreboardManager(private val plugin: Mistaken) {

    private val boards = ConcurrentHashMap<UUID, FastBoard>()
    private val mm = plugin.mm
    private val legacy = LegacyComponentSerializer.legacySection()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    init {
        startUpdateTask()
    }

    private fun startUpdateTask() {
        updateJob = scope.launch {
            // Esperar a que el patrón de la luz verde
            while (isActive && !plugin.isReady) delay(500)

            while (isActive) {
                try {
                    updateAll()
                } catch (e: Exception) {
                    // Si algo truena, que no se caiga el server, pariente
                }
                delay(500L) // Actualiza cada 10 Ticks (2 veces por segundo)
            }
        }
    }

    /**
     * Actualiza a un solo jugador (útil cuando le bajan vida).
     */
    fun updatePlayer(player: Player) {
        val board = boards[player.uniqueId] ?: return
        if (!player.isOnline || !plugin.isReady) return
        scope.launch { renderBoard(player, board) }
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

        // 1. RECOLECCIÓN DE DATOS
        val onlineCount = Bukkit.getOnlinePlayers().size
        val lives = gm.combatManager.getHealth(player).toString()

        // Formateo de tiempo (00:00 o s)
        val timeStr = if (gm.timer >= 60) {
            String.format("%02d:%02d", gm.timer / 60, gm.timer % 60)
        } else {
            "${gm.timer}"
        }

        val mapName = gm.currentMapName
        val completed = plugin.generatorManager.getCompletedCount().toString()
        val total = plugin.generatorManager.getTotalGenerators().toString()

        val state = gm.currentState
        val mode = gm.currentMode

        // 2. 🔥 CARGA DE CONFIGURACIÓN
        // Buscamos en el archivo "messages" de la carpeta del idioma del jugador
        val config = plugin.messageConfig.getSpecificFile(player, "messages")

        // 3. DETERMINAR RUTA SEGÚN TU YAML
        // scoreboard:
        //   lobby: ...
        //   ingame_classic: ...
        val stateKey = when (state) {
            GameState.INGAME -> "ingame_${mode.name.lowercase()}"
            else -> state.name.lowercase()
        }
        val path = "scoreboard.$stateKey"

        // 4. ACTUALIZAR TÍTULO
        val rawTitle = config.getString("scoreboard.title") ?: "<gradient:#88C6F2:#4386B5><bold>MISTAKEN"
        board.updateTitle(legacy.serialize(mm.deserialize(rawTitle)))

        // 5. PROCESAR LÍNEAS
        val rawLines = config.getStringList(path)
        if (rawLines.isEmpty()) {
            // Si te sale esto en el juego, es que la ruta en el YAML está mal, carnal
            board.updateLines(listOf("§cError: $path", "§cvacío en YAML"))
            return
        }

        val processedLines = mutableListOf<String>()

        // Cache de asesinos para no procesar el loop por cada línea
        val killerLines = if (rawLines.any { it.contains("%killers%") }) getKillerDisplayStrings(gm) else emptyList()

        for (line in rawLines) {
            if (line.contains("%killers%")) {
                processedLines.addAll(killerLines)
                continue
            }

            // Reemplazo de placeholders al puro centavo
            val formatted = line
                .replace("%player%", player.name)
                .replace("%timer%", timeStr)
                .replace("%map%", mapName)
                .replace("%online%", onlineCount.toString())
                .replace("%completed%", completed)
                .replace("%total%", total)
                .replace("%lives%", lives)
                .replace("{", "<").replace("}", ">") // Fix por si usas llaves en vez de <>

            // Convertimos de MiniMessage a Legacy (§) para que FastBoard mande el paquete
            processedLines.add(legacy.serialize(mm.deserialize(formatted)))
        }

        // 6. ENVIAR PAQUETES
        board.updateLines(processedLines)
    }

    private fun getKillerDisplayStrings(gm: GameManager): List<String> {
        val lines = mutableListOf<String>()
        val ids = gm.asesinosUUIDs

        if (ids.isEmpty()) {
            lines.add(legacy.serialize(mm.deserialize(" <gray>Ninguno")))
            return lines
        }

        for (id in ids) {
            val killer = Bukkit.getPlayer(id)
            if (killer != null && killer.isOnline) {
                // Estilo tétrico para la lista de asesinos
                lines.add(legacy.serialize(mm.deserialize(" <white>• <red>${killer.name}")))
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

    fun removePlayer(player: Player) {
        boards.remove(player.uniqueId)?.let { if (!it.isDeleted) it.delete() }
    }

    fun removeAll() {
        updateJob?.cancel()
        boards.values.forEach { if (!it.isDeleted) it.delete() }
        boards.clear()
        scope.cancel()
    }
}
