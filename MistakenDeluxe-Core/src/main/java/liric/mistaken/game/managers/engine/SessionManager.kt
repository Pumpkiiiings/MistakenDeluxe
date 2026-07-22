package liric.mistaken.game.managers.engine

import liric.mistaken.Mistaken
import liric.mistaken.api.events.MistakenPlayerJoinSessionEvent
import liric.mistaken.api.events.MistakenPlayerLeaveSessionEvent
import liric.mistaken.game.GameSession
import liric.mistaken.utils.proxy.BungeeUtils
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.api.managers.ISessionManager
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

        // 🔥 Disparar evento a la API
        val event = MistakenPlayerJoinSessionEvent(player, session)
        org.bukkit.Bukkit.getPluginManager().callEvent(event)
    }

    override fun leaveSession(player: Player) {
        val sessionId = playerSessions.remove(player.uniqueId) ?: return
        val session = activeSessions[sessionId]
        session?.removePlayer(player)

        // 🔥 Disparar evento a la API
        if (session != null) {
            val event = MistakenPlayerLeaveSessionEvent(player, session)
            org.bukkit.Bukkit.getPluginManager().callEvent(event)
        }

        // 🔥 FIX: Al salir de la sesión, aseguramos que el jugador se limpie completamente
        // del modo espectador y se vuelva a modo supervivencia (vital para Multiarena)
        plugin.spectatorManager.removeCustomSpectator(player)
        player.gameMode = GameMode.SURVIVAL

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
        // Remover de playerSessions a los que estaban allí
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
