package liric.mistaken.game.managers.engine

import io.papermc.paper.event.player.AsyncChatEvent
import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class IsolationManager(private val plugin: Mistaken) : Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * 🔥 AISLAMIENTO DE TAB Y VISIBILIDAD (Folia Ready)
     */
    fun updateVisibility(target: Player) {
        val sessionManager = plugin.sessionManager ?: return
        val targetSessionId = sessionManager.playerSessions[target.uniqueId]

        for (online in plugin.server.onlinePlayers) {
            if (online == target) continue

            val onlineSessionId = sessionManager.playerSessions[online.uniqueId]
            val shareSession = targetSessionId == onlineSessionId

            // Actualizar la vista del Target en SU hilo
            target.scheduler.run(plugin, { _ ->
                if (target.isOnline && online.isOnline) {
                    if (shareSession) target.showPlayer(plugin, online)
                    else target.hidePlayer(plugin, online)
                }
            }, null)

            // Actualizar la vista del otro jugador en SU hilo
            online.scheduler.run(plugin, { _ ->
                if (online.isOnline && target.isOnline) {
                    if (shareSession) online.showPlayer(plugin, target)
                    else online.hidePlayer(plugin, target)
                }
            }, null)
        }
    }

    /**
     * 🔥 AISLAMIENTO DE CHAT
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onIsolatedChat(event: AsyncChatEvent) {
        val sessionManager = plugin.sessionManager ?: return
        val senderSessionId = sessionManager.playerSessions[event.player.uniqueId]

        event.viewers().removeIf { viewer ->
            if (viewer is Player) {
                val viewerSessionId = sessionManager.playerSessions[viewer.uniqueId]
                viewerSessionId != senderSessionId
            } else {
                false // La consola lee todo
            }
        }
    }
}