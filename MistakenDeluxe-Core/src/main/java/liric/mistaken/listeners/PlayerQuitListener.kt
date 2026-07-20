package liric.mistaken.listeners

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
 * FIX: Ahora detecta la sesión específica del jugador para procesar su salida.
 */
class PlayerQuitListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 1. BUSCAR LA SESIÓN DEL JUGADOR
        val session = plugin.sessionManager.getSession(player)

        // 2. LÓGICA DE JUEGO (Si estaba en una partida)
        if (session != null) {
            if (session.currentState == GameState.INGAME) {

                // Si el jugador es un asesino en SU sesión
                if (session.isKiller(uuid)) {
                    // Limpiar visuales
                    plugin.asesinoManager.removeKiller(player)
                    // Quitar de la lista de la sesión
                    session.asesinosUUIDs.remove(uuid)

                    // Si ya no quedan asesinos en esa partida, termina
                    if (session.asesinosUUIDs.isEmpty()) {
                        session.stateController.endGame("game.killer-disconnected", false)
                    }
                } else {
                    // Si era superviviente, procesamos su "muerte" por desconexión en esa partida
                    session.playerController.handlePlayerDeath(player)
                }
            }

            // Limpieza interna de la sesión
            session.asesinosUUIDs.remove(uuid)
            if (session.currentKillerUUID == uuid) {
                session.currentKillerUUID = null
            }

            // 🔥 IMPORTANTE: Notificar al SessionManager que el jugador abandonó la instancia
            plugin.sessionManager.leaveSession(player)
        }

        // 3. LIMPIEZA GLOBAL DE MEMORIA
        plugin.combatManager.removePlayerData(uuid)
        plugin.scoreboardManager.removePlayer(player)
        plugin.ambientManager.stopAmbience(player)
        plugin.statsManager.unloadPlayer(uuid)

        // 4. PERSISTENCIA DE DATOS (LuckPerms y Archivos)
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            // Limpiar Prefijos de LuckPerms
            try {
                if (plugin.server.pluginManager.isPluginEnabled("LuckPerms")) {
                    val lp = LuckPermsProvider.get()
                    lp.userManager.modifyUser(uuid) { user ->
                        user.data().clear { node -> node is PrefixNode }
                    }
                }
            } catch (ignored: Exception) {}

            // Guardar y descargar datos
            plugin.playerDataManager.saveConfigSync()
            plugin.playerDataManager.removeData(uuid)
        }
    }
}
