package liric.mistaken.listeners

import liric.mistaken.Mistaken
import liric.mistaken.menu.menus.PrivateLobbyMenu
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class PrivateGameInteractListener(private val plugin: Mistaken) : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val action = event.action

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            val item = event.item ?: return
            
            if (item.type == Material.COMMAND_BLOCK && item.hasItemMeta()) {
                val meta = item.itemMeta
                if (meta.displayName == "§6§lPanel de Control") {
                    event.isCancelled = true
                    val session = plugin.sessionManager.getSession(player)
                    if (session != null && session.isPrivate) {
                        PrivateLobbyMenu(plugin, session).abrir(player)
                    }
                }
            }
        }
    }
}
