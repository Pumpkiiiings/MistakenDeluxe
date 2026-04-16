package liric.mistaken.game

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.game.logic.*
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 *[LIRIC-MISTAKEN 2.0]
 * GameSession: Instancia aislada de partida adaptada.
 */
class GameSession(val plugin: Mistaken, val sessionId: String, mapName: String = "Esperando...") {

    val players = ConcurrentHashMap.newKeySet<UUID>()

    var currentState = GameState.LOBBY
    var currentMode = MistakenMode.CLASSIC
    var timer = 0
    var currentMapName = mapName
    var modeForced = false

    var currentAsesinoUUID: UUID? = null
    var lastKillerWon: Boolean = false

    val asesinosUUIDs = ConcurrentHashMap.newKeySet<UUID>()
    val yaJugaronAsesino = ConcurrentHashMap.newKeySet<UUID>()
    val changedBlocks = ConcurrentHashMap<org.bukkit.Location, org.bukkit.Material>()

    // Utiliza el assert !! porque sabemos que si estamos en una partida,
    // el plugin instanció los managers correctamente
    val voteManager = plugin.voteManager!!
    val ambientManager = plugin.ambientManager!!
    val combatManager = plugin.combatManager!!

    val stateController = GameStateController(this)
    val playerController = GamePlayerController(this)
    val uiController = GameUIController(this)
    val worldController = GameWorldController(this)
    private val loopTask = GameLoopTask(this)

    init {
        loopTask.start()
        plugin.componentLogger.info(plugin.mm.deserialize("<green>Sesión <yellow>$sessionId</yellow> iniciada.</green>"))
    }

    fun addPlayer(player: Player) {
        players.add(player.uniqueId)
    }

    fun removePlayer(player: Player) {
        players.remove(player.uniqueId)
        asesinosUUIDs.remove(player.uniqueId)
        if (currentAsesinoUUID == player.uniqueId) currentAsesinoUUID = null
    }

    fun getPlayers(): List<Player> {
        return if (plugin.serverMode == "MULTIARENA") {
            players.mapNotNull { plugin.server.getPlayer(it) }.filter { it.isOnline }
        } else {
            plugin.server.onlinePlayers.toList()
        }
    }

    fun getCurrentAsesino(): Player? = currentAsesinoUUID?.let { plugin.server.getPlayer(it) }
    fun esAsesino(uuid: UUID): Boolean = asesinosUUIDs.contains(uuid)

    fun broadcastLocalized(path: String, vararg tags: net.kyori.adventure.text.minimessage.tag.resolver.TagResolver) {
        val message = plugin.messageConfig.getMessage(null, path, *tags)
        getPlayers().forEach { p -> p.sendMessage(message) }
    }

    fun shutdown() {
        loopTask.stop()
        getPlayers().forEach { plugin.sessionManager?.leaveSession(it) }
        players.clear()
        changedBlocks.clear()
        plugin.componentLogger.info(plugin.mm.deserialize("<red>Sesión <yellow>$sessionId</yellow> destruida.</red>"))
    }
}
