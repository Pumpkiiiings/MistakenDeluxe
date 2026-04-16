package liric.mistaken.listeners

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 *[LIRIC-MISTAKEN 2.0]
 * PlayerQuitListener: Limpieza profunda y segura.
 * FIX: Operadores de seguridad `?.` para garantizar que en un LOBBY no tire errores.
 */
class PlayerQuitListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 🔥 Ocultamos el mensaje por defecto
        event.quitMessage(null)

        // 1. BUSCAR LA SESIÓN DEL JUGADOR (Llamada segura)
        val session = plugin.sessionManager?.getSession(player)

        // 2. LÓGICA DE JUEGO (Si estaba en una partida o existe el sessionManager)
        if (session != null) {
            if (session.currentState == GameState.INGAME) {

                if (session.esAsesino(uuid)) {
                    plugin.asesinoManager?.removerAsesino(player) // o removeAsesino (ajusta al nombre de tu método)
                    session.asesinosUUIDs.remove(uuid)

                    if (session.asesinosUUIDs.isEmpty()) {
                        session.stateController.endGame("game.killer-disconnected", false)
                    }
                } else {
                    session.playerController.handlePlayerDeath(player)
                }
            }

            session.asesinosUUIDs.remove(uuid)
            if (session.currentAsesinoUUID == uuid) {
                session.currentAsesinoUUID = null
            }

            plugin.sessionManager?.leaveSession(player)
        }

        // 3. LIMPIEZA GLOBAL DE MEMORIA (Sistemas de Juego - Safe Calls)
        plugin.combatManager?.removePlayerData(uuid)
        plugin.ambientManager?.stopAmbience(player)

        // Sistemas Core (Siempre existen, llamada directa)
        plugin.scoreboardManager.removePlayer(player)
        plugin.statsManager.unloadPlayer(uuid)

        // Limpiar listas de estados del jugador
        plugin.staffEditMode.remove(uuid)
        plugin.afkPlayers.remove(uuid)

        // 4. PERSISTENCIA DE DATOS (Asíncrona)
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                if (plugin.server.pluginManager.isPluginEnabled("LuckPerms")) {
                    val lp = net.luckperms.api.LuckPermsProvider.get()
                    lp.userManager.modifyUser(uuid) { user ->
                        user.data().clear { node -> node is net.luckperms.api.node.types.PrefixNode }
                    }
                }
            } catch (ignored: Exception) {}

            plugin.playerDataManager.saveConfigSync()
            plugin.playerDataManager.removeData(uuid)
        }
    }
}
