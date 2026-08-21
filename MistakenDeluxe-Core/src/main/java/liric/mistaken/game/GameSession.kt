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
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService


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
    val forcedSurvivorUUIDs = ConcurrentHashMap.newKeySet<UUID>()
    var isDebugStart = false

    var currentKillerUUID: UUID? = null
    var lastKillerWon: Boolean = false

    override val killersUUIDs = ConcurrentHashMap.newKeySet<UUID>()
    val yaJugaronKiller = ConcurrentHashMap.newKeySet<UUID>()
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
        plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>Session $id started.</gray>"))
    }

    // --- MÉTODOS DE JUGADORES ---
    fun addPlayer(player: Player) {
        players.add(player.uniqueId)
    }

    fun removePlayer(player: Player) {
        players.remove(player.uniqueId)
        killersUUIDs.remove(player.uniqueId)
        if (currentKillerUUID == player.uniqueId) currentKillerUUID = null
        uiController.hideBossBar(player)
        plugin.observerHUDManager.clearPlayer(player)
    }

    fun getPlayers(): List<Player> {
        return players.mapNotNull { plugin.server.getPlayer(it) }.filter { it.isOnline }
    }

    // --- GETTERS ÚTILES ---
    fun getCurrentKiller(): Player? = currentKillerUUID?.let { plugin.server.getPlayer(it) }
    override fun isKiller(uuid: UUID): Boolean = killersUUIDs.contains(uuid)

    // Solo envía messages a los players DE ESTA SESIÓN
    fun broadcastLocalized(path: String, vararg tags: TagResolver) {
        val message = MessageService.getComponent(null, path, *tags)
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
        plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>Session $id destroyed.</gray>"))
    }
    override fun forceStart() {
        stateController.startInGame()
    }

    override fun forceEnd(killerWon: Boolean) {
        stateController.endGame("game.forced-end", killerWon)
    }

    override val survivorsUUIDs: Set<UUID>
        get() = players.filter { uuid -> !isKiller(uuid) && plugin.server.getPlayer(uuid)?.let { !plugin.spectatorManager.isSpectator(it) } ?: false }.toSet()

    override val aliveSurvivorsUUIDs: Set<UUID>
        get() = survivorsUUIDs.filter { uuid ->
            val p = plugin.server.getPlayer(uuid)
            p != null && p.health > 0
        }.toSet()

    override val spectatorsUUIDs: Set<UUID>
        get() = players.filter { uuid -> plugin.server.getPlayer(uuid)?.let { plugin.spectatorManager.isSpectator(it) } ?: false }.toSet()
}

