package liric.mistaken.game

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.game.logic.*
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameManager(val plugin: Mistaken) {

    // --- ESTADO DEL JUEGO ---
    var currentState = GameState.LOBBY
    var currentMode = MistakenMode.CLASSIC
    var timer = 0
    var currentMapName = "Esperando..."
    var modeForced = false

    var currentAsesinoUUID: UUID? = null
    val asesinosUUIDs = ConcurrentHashMap.newKeySet<UUID>()
    val yaJugaronAsesino = ConcurrentHashMap.newKeySet<UUID>()
    val changedBlocks = ConcurrentHashMap<org.bukkit.Location, org.bukkit.Material>()

    // --- MANAGERS EXISTENTES ---
    val voteManager = plugin.voteManager // Asumiendo que se inicializa en el plugin
    val ambientManager = plugin.ambientManager
    val combatManager = plugin.combatManager

    // --- CONTROLADORES DE LÓGICA ---
    val stateController = GameStateController(this)
    val playerController = GamePlayerController(this)
    val uiController = GameUIController(this)
    val worldController = GameWorldController(this)
    private val loopTask = GameLoopTask(this)

    init {
        loopTask.start()
    }

    // --- GETTERS ÚTILES ---
    fun getCurrentAsesino(): Player? = currentAsesinoUUID?.let { plugin.server.getPlayer(it) }
    fun esAsesino(uuid: UUID): Boolean = asesinosUUIDs.contains(uuid)

    fun broadcastLocalized(path: String, vararg tags: net.kyori.adventure.text.minimessage.tag.resolver.TagResolver) {
        val message = plugin.messageConfig.getMessage(null, path, *tags)
        plugin.server.onlinePlayers.forEach { p -> p.sendMessage(message) }
    }
}
