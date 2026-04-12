package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession // 🔥 IMPORT FALTANTE AÑADIDO
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SessionManager(private val plugin: Mistaken) {

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

    fun createSession(mapName: String): GameSession {
        val id = generateShortId()
        val session = GameSession(plugin, id, mapName)
        activeSessions[id] = session
        return session
    }

    fun joinSession(player: Player, sessionId: String) {
        val session = activeSessions[sessionId] ?: return
        leaveSession(player)
        playerSessions[player.uniqueId] = sessionId
        session.addPlayer(player)
        plugin.isolationManager.updateVisibility(player)
    }

    fun leaveSession(player: Player) {
        val sessionId = playerSessions.remove(player.uniqueId) ?: return
        val session = activeSessions[sessionId]
        session?.removePlayer(player)

        if (plugin.config.getString("server-mode") == "VELOCITY") {
            val lobbyName = plugin.config.getString("proxy-lobby-server", "lobby") ?: "lobby"
            liric.mistaken.utils.BungeeUtils.sendToServer(plugin, player, lobbyName)
        } else {
            plugin.lobbyLocation?.let { player.teleportAsync(it) }
            plugin.isolationManager.updateVisibility(player)
        }
    }

    fun getSession(player: Player): GameSession? {
        val id = playerSessions[player.uniqueId] ?: return null
        return activeSessions[id]
    }
}
