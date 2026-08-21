package liric.mistaken.game.managers.engine

import liric.mistaken.Mistaken
import liric.mistaken.api.events.MistakenPlayerJoinSessionEvent
import liric.mistaken.api.events.MistakenPlayerLeaveSessionEvent
import liric.mistaken.game.GameSession
import liric.mistaken.utils.misc.BungeeUtils
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.api.managers.ISessionManager
import liric.mistaken.listeners.player.PlayerListener
import org.bukkit.Bukkit
import org.bukkit.GameMode

class SessionManager(private val plugin: Mistaken) : ISessionManager {

    val activeSessions = ConcurrentHashMap<String, GameSession>()
    val playerSessions = ConcurrentHashMap<UUID, String>()

    private fun generateShortId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var id: String
        do {
            id = (1..4).map { chars.random() }.joinToString("")
        } while (activeSessions.containsKey(id))
        return id
    }

    fun createSession(mapName: String, isPrivate: Boolean = false): GameSession {
        var id = generateShortId()
        if (isPrivate) id += "-P"
        val session = GameSession(plugin, id, mapName, isPrivate)
        activeSessions[id] = session
        return session
    }

    override fun joinSession(player: Player, sessionId: String) {
        val session = activeSessions[sessionId] ?: return
        leaveSession(player)
        playerSessions[player.uniqueId] = sessionId
        session.addPlayer(player)
        plugin.isolationManager.updateVisibility(player)

        
        val event = MistakenPlayerJoinSessionEvent(player, session)
        Bukkit.getPluginManager().callEvent(event)
    }

    override fun leaveSession(player: Player) {
        val sessionId = playerSessions.remove(player.uniqueId) ?: return
        val session = activeSessions[sessionId]
        session?.removePlayer(player)

        
        if (session != null) {
            val event = MistakenPlayerLeaveSessionEvent(player, session)
            Bukkit.getPluginManager().callEvent(event)
        }

        
        
        plugin.spectatorManager.removeCustomSpectator(player)
        PlayerListener.resetPlayerStatus(player)

        val serverMode = plugin.serverMode

        if (serverMode.equals("GAME_SERVER", ignoreCase = true)) {
            val lobbyName = plugin.config.getString("proxy-lobby-server", "lobby") ?: "lobby"
            BungeeUtils.sendToServer(plugin, player, lobbyName)
        } else {
            plugin.lobbyLocation?.let { loc ->
                player.teleportAsync(loc).thenAccept {
                    plugin.isolationManager.updateVisibility(player)
                }
            } ?: plugin.isolationManager.updateVisibility(player)
        }
    }

    fun destroySession(sessionId: String) {
        val session = activeSessions.remove(sessionId) ?: return
        session.shutdown()
        
        val toRemove = playerSessions.filterValues { it == sessionId }.keys
        toRemove.forEach { playerSessions.remove(it) }
    }

    override fun getSession(player: Player): GameSession? {
        val id = playerSessions[player.uniqueId] ?: return null
        return activeSessions[id]
    }

    override fun getSession(uuid: UUID): GameSession? {
        val id = playerSessions[uuid] ?: return null
        return activeSessions[id]
    }
}
