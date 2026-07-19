package liric.mistaken.game.managers.engine

import io.papermc.paper.event.player.AsyncChatEvent
import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class IsolationManager(private val plugin: Mistaken) : Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * 🔥 AISLAMIENTO DE TAB Y VISIBILIDAD
     * Oculta a los jugadores que no están en tu misma partida/lobby.
     * Al usar hidePlayer, Paper automáticamente los borra del TAB.
     */
    fun updateVisibility(target: Player) {
        val targetSessionId = plugin.sessionManager.playerSessions[target.uniqueId]

        for (online in Bukkit.getOnlinePlayers()) {
            if (online == target) continue

            val onlineSessionId = plugin.sessionManager.playerSessions[online.uniqueId]

            // Si están en la misma partida (o ambos están en el Lobby) se ven
            if (targetSessionId == onlineSessionId) {
                plugin.visibilityManager.showPlayer(target, online)
                plugin.visibilityManager.showPlayer(online, target)
            } else {
                // Si están en partidas distintas, se vuelven inexistentes el uno para el otro
                plugin.visibilityManager.hidePlayer(target, online)
                plugin.visibilityManager.hidePlayer(online, target)
            }
        }
    }

    /**
     * 🔥 AISLAMIENTO DE CHAT
     * Si mandas un mensaje, solo los de tu partida lo leen.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onIsolatedChat(event: AsyncChatEvent) {
        val senderSessionId = plugin.sessionManager.playerSessions[event.player.uniqueId]

        // Filtramos a quién le llega el mensaje
        event.viewers().removeIf { viewer ->
            if (viewer is Player) {
                val viewerSessionId = plugin.sessionManager.playerSessions[viewer.uniqueId]
                // Si el ID de sesión es distinto, eliminamos al receptor de la lista
                viewerSessionId != senderSessionId
            } else {
                false // Consola siempre lee todo
            }
        }
    }
}