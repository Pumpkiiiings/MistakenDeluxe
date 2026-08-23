package liric.mistaken.listeners.player

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.types.PrefixNode

/**
 * [LIRIC-MISTAKEN 2.0]
 * PlayerQuitListener: Limpieza profunda adaptada a MULTIARENA.
 * FIX: Ahora detecta la sesión específica del player para procesar su salida.
 */
class PlayerQuitListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        
        val session = plugin.sessionManager.getSession(player)

        
        if (session != null) {
            if (session.currentState == GameState.INGAME) {

                
                if (session.isKiller(uuid)) {
                    
                    plugin.killerManager.removeKiller(player)
                    
                    session.killersUUIDs.remove(uuid)

                    
                    if (session.killersUUIDs.isEmpty()) {
                        session.stateController.endGame("game.killer-disconnected", false)
                    }
                } else {
                    
                    session.playerController.handlePlayerDeath(player)
                }
            }

            
            session.killersUUIDs.remove(uuid)
            if (session.currentKillerUUID == uuid) {
                session.currentKillerUUID = null
            }

            
            plugin.sessionManager.leaveSession(player)
        }

        
        // Limpieza de estados globales — evita UUIDs fantasma al reconectarse
        plugin.afkPlayers.remove(uuid)
        plugin.staffEditMode.remove(uuid)

        plugin.observerHUDManager.clearPlayer(player)
        plugin.flashlightManager.clear(uuid)
        plugin.combatManager.removePlayerData(uuid)
        plugin.scoreboardManager.removePlayer(player)
        plugin.ambientManager.stopAmbience(player)
        plugin.statsManager.unloadPlayer(uuid)

        
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            
            try {
                if (plugin.server.pluginManager.isPluginEnabled("LuckPerms")) {
                    val lp = LuckPermsProvider.get()
                    lp.userManager.modifyUser(uuid) { user ->
                        user.data().clear { node -> node is PrefixNode }
                    }
                }
            } catch (ignored: Exception) {}

            
            plugin.playerDataManager.saveConfigSync()
            plugin.playerDataManager.removeData(uuid)
        }
    }
}
