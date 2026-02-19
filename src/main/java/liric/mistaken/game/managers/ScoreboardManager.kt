package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.utils.FastBoard
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * ScoreboardManager: Motor ultra-optimizado.
 * - Procesa MiniMessage -> Legacy en hilos secundarios (Async).
 * - Usa FastBoard para enviar paquetes sin tocar el Main Thread de Bukkit.
 */
class ScoreboardManager(private val plugin: Mistaken) {

    private val boards = ConcurrentHashMap<UUID, FastBoard>()
    private val mm = MiniMessage.miniMessage()
    private val legacy = LegacyComponentSerializer.legacySection()

    // Scope para procesamiento asíncrono
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    init {
        startUpdateTask()
    }

    private fun startUpdateTask() {
        updateJob = scope.launch {
            // Esperar a que el plugin esté listo (Usando el flag que creamos antes)
            while (isActive && !plugin.isReady) {
                delay(500)
            }

            while (isActive) {
                try {
                    // 1. Recolección de datos rápida (Thread-Safe en Paper)
                    val onlineCount = Bukkit.getOnlinePlayers().size
                    val gm = plugin.gameManager

                    // 2. Procesar todos los marcadores
                    updateAll(onlineCount, gm)
                } catch (e: Exception) {
                    // Evitar que el hilo muera por un error de config
                }
                delay(500L) // 10 Ticks: Balance perfecto entre fluidez y rendimiento
            }
        }
    }

    private fun updateAll(online: Int, gm: GameManager) {
        val state = gm.currentState
        val mode = gm.currentMode

        // --- PRE-PROCESAMIENTO GLOBAL (Se hace 1 vez para todos los jugadores) ---
        val timeStr = String.format("%02d:%02d", gm.timer / 60, gm.timer % 60)
        val mapName = gm.currentMapName ?: "---"
        val completed = plugin.generatorManager.getCompletedCount().toString()
        val total = plugin.generatorManager.getTotalGenerators().toString()
        val killers = getKillerDisplayStrings(gm)

        // Definir la ruta del YAML según tu estructura
        val stateKey = if (state == GameState.INGAME) "ingame_${mode.name.lowercase()}" else state.name.lowercase()
        val path = "scoreboard.$stateKey"

        // Iterar sobre los marcadores activos
        boards.forEach { (uuid, board) ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            if (!player.isOnline) return@forEach

            // Obtener idioma del jugador
            val lang = plugin.playerDataManager.getLanguage(uuid) ?: "es"
            val config = plugin.messageConfig.getLangConfig(lang)

            // 1. Actualizar Título
            val rawTitle = config.getString("scoreboard.title") ?: "<bold>MISTAKEN"
            board.updateTitle(legacy.serialize(mm.deserialize(rawTitle)))

            // 2. Procesar Líneas
            val rawLines = config.getStringList(path)
            val processedLines = mutableListOf<String>()
            val lives = gm.combatManager.getHealth(player).toString()

            for (line in rawLines) {
                if (line.contains("%killers%")) {
                    processedLines.addAll(killers)
                    continue
                }

                // Reemplazo de placeholders ultra-rápido
                val formatted = line
                    .replace("%timer%", timeStr)
                    .replace("%map%", mapName)
                    .replace("%online%", online.toString())
                    .replace("%completed%", completed)
                    .replace("%total%", total)
                    .replace("%lives%", lives)
                    .replace("%player%", player.name)

                // Convertir MiniMessage a Legacy (§) para los paquetes de FastBoard
                processedLines.add(legacy.serialize(mm.deserialize(formatted)))
            }

            // 3. Inyectar al marcador (FastBoard maneja los paquetes de forma interna)
            board.updateLines(processedLines)
        }
    }

    private fun getKillerDisplayStrings(gm: GameManager): List<String> {
        val lines = mutableListOf<String>()
        val ids = gm.asesinosUUIDs
        if (ids.isEmpty()) {
            lines.add(legacy.serialize(mm.deserialize(" <red>Buscando...")))
            return lines
        }
        ids.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let {
                lines.add(legacy.serialize(mm.deserialize(" <red>• ${it.name}")))
            }
        }
        return lines
    }

    // --- GESTIÓN DE JUGADORES (CORREGIDO PARA KOTLIN) ---

    fun addPlayer(player: Player) {
        val uuid = player.uniqueId
        // Limpiamos rastro anterior si existe
        boards.remove(uuid)?.let { old ->
            if (!old.isDeleted) old.delete()
        }
        // Creamos el nuevo marcador
        boards[uuid] = FastBoard(player)
    }

    fun removePlayer(player: Player) {
        val uuid = player.uniqueId
        boards.remove(uuid)?.let { board ->
            if (!board.isDeleted) board.delete()
        }
    }

    // Sobrecarga por UUID para mayor utilidad
    fun removePlayer(uuid: UUID) {
        boards.remove(uuid)?.let { board ->
            if (!board.isDeleted) board.delete()
        }
    }

    fun removeAll() {
        updateJob?.cancel()
        boards.values.forEach { if (!it.isDeleted) it.delete() }
        boards.clear()
        scope.cancel()
    }
}
