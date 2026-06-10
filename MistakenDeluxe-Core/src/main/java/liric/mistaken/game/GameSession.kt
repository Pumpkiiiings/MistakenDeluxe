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
 * GameSession: Una instancia aislada de una partida.
 * Soporta modo Multiarena y Velocity.
 */
class GameSession(
    val plugin: Mistaken,
    override val id: String,
    val mapName: String = "Esperando..."
) : liric.mistaken.api.managers.ISession {

    // --- JUGADORES AISLADOS DE ESTA SESIÓN ---
    val players = ConcurrentHashMap.newKeySet<UUID>()

    // --- ESTADO DEL JUEGO ---
    override var currentState = GameState.LOBBY
    var currentMode = MistakenMode.CLASSIC
    var timer = 0
    var currentMapName = mapName
    var modeForced = false
    var forceStart = false

    var currentAsesinoUUID: UUID? = null
    var lastKillerWon: Boolean = false

    override val asesinosUUIDs = ConcurrentHashMap.newKeySet<UUID>()
    val yaJugaronAsesino = ConcurrentHashMap.newKeySet<UUID>()
    val changedBlocks = ConcurrentHashMap<org.bukkit.Location, org.bukkit.Material>()

    // --- MANAGERS GLOBALES (Compartidos) ---
    val voteManager = plugin.voteManager
    val ambientManager = plugin.ambientManager
    val combatManager = plugin.combatManager

    // --- CONTROLADORES DE LÓGICA (Instanciados POR SESIÓN) ---
    val stateController = GameStateController(this)
    val playerController = GamePlayerController(this)
    val uiController = GameUIController(this)
    val worldController = GameWorldController(this)
    private val loopTask = GameLoopTask(this)

    init {
        loopTask.start()
        plugin.componentLogger.info(plugin.mm.deserialize("<green>Sesión <yellow>$id</yellow> iniciada.</green>"))
    }

    // --- MÉTODOS DE JUGADORES ---
    fun addPlayer(player: Player) {
        players.add(player.uniqueId)
    }

    fun removePlayer(player: Player) {
        players.remove(player.uniqueId)
        asesinosUUIDs.remove(player.uniqueId)
        if (currentAsesinoUUID == player.uniqueId) currentAsesinoUUID = null
    }

    fun getPlayers(): List<Player> {
        return players.mapNotNull { plugin.server.getPlayer(it) }.filter { it.isOnline }
    }

    // --- GETTERS ÚTILES ---
    fun getCurrentAsesino(): Player? = currentAsesinoUUID?.let { plugin.server.getPlayer(it) }
    override fun esAsesino(uuid: UUID): Boolean = asesinosUUIDs.contains(uuid)

    // Solo envía mensajes a los jugadores DE ESTA SESIÓN
    fun broadcastLocalized(path: String, vararg tags: net.kyori.adventure.text.minimessage.tag.resolver.TagResolver) {
        val message = plugin.messageConfig.getMessage(null, path, *tags)
        getPlayers().forEach { p -> p.sendMessage(message) }
    }

    fun shutdown() {
        loopTask.stop()
        getPlayers().forEach { plugin.sessionManager.leaveSession(it) }
        players.clear()
        changedBlocks.clear()
        plugin.componentLogger.info(plugin.mm.deserialize("<red>Sesión <yellow>$id</yellow> destruida.</red>"))
    }
}
