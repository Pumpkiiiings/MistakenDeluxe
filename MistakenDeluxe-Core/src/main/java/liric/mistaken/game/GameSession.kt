package liric.mistaken.game

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.game.logic.*
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.api.managers.ISession
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Location
import org.bukkit.Material
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

/**
 *[LIRIC-MISTAKEN 2.0]
 * GameSession: Una instancia aislada de una partida.
 * Soporta modo Multiarena y Velocity.
 */
class GameSession(
    val plugin: Mistaken,
    override val id: String,
    val mapName: String = "Esperando...",
    val isPrivate: Boolean = false
) : ISession {

    var settings: PrivateGameSettings? = if (isPrivate) PrivateGameSettings() else null

    // --- JUGADORES AISLADOS DE ESTA SESIÓN ---
    val players = ConcurrentHashMap.newKeySet<UUID>()

    // --- ESTADO DEL JUEGO ---
    override var currentState = GameState.LOBBY
    var currentMode = MistakenMode.CLASSIC
    var timer = 0
    var currentMapName = mapName
    var modeForced = false
    var forceStart = false
    var forcedKillerUUID: UUID? = null

    var currentKillerUUID: UUID? = null
    var lastKillerWon: Boolean = false

    override val asesinosUUIDs = ConcurrentHashMap.newKeySet<UUID>()
    val yaJugaronAsesino = ConcurrentHashMap.newKeySet<UUID>()
    val changedBlocks = ConcurrentHashMap<Location, Material>()

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
        plugin.componentLogger.info(ColorTranslator.translate("[INFO] [Session] Session $id started."))
    }

    // --- MÉTODOS DE JUGADORES ---
    fun addPlayer(player: Player) {
        players.add(player.uniqueId)
    }

    fun removePlayer(player: Player) {
        players.remove(player.uniqueId)
        asesinosUUIDs.remove(player.uniqueId)
        if (currentKillerUUID == player.uniqueId) currentKillerUUID = null
        uiController.hideBossBar(player)
        plugin.observerHUDManager.clearPlayer(player)
    }

    fun getPlayers(): List<Player> {
        return players.mapNotNull { plugin.server.getPlayer(it) }.filter { it.isOnline }
    }

    // --- GETTERS ÚTILES ---
    fun getCurrentAsesino(): Player? = currentKillerUUID?.let { plugin.server.getPlayer(it) }
    override fun isKiller(uuid: UUID): Boolean = asesinosUUIDs.contains(uuid)

    // Solo envía mensajes a los jugadores DE ESTA SESIÓN
    fun broadcastLocalized(path: String, vararg tags: TagResolver) {
        val message = PumpkingServiceManager.messages.getComponent(null, path, *tags)
        getPlayers().forEach { p -> p.sendMessage(message) }
    }

    fun shutdown() {
        loopTask.stop()
        // FIX #7: Snapshot the player list before iterating.
        // leaveSession() → removePlayer() modifies `players` concurrently.
        // Taking a snapshot first makes the iteration deterministic and prevents
        // any ambiguous state between leaveSession and the final players.clear().
        val snapshot = getPlayers().toList()
        snapshot.forEach { plugin.sessionManager.leaveSession(it) }
        players.clear()       // defensive clear for any UUIDs whose Player was offline
        changedBlocks.clear()
        plugin.componentLogger.info(ColorTranslator.translate("[INFO] [Session] Session $id destroyed."))
    }
}

