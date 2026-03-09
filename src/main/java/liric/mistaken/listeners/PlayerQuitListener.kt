package liric.mistaken.listeners

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 *[LIRIC-MISTAKEN 2.0]
 * PlayerQuitListener: Limpieza profunda y persistencia.
 * FIX: Llamadas actualizadas a la nueva lógica modular de GameManager.
 */
class PlayerQuitListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 1. LÓGICA DE JUEGO (Hilo Principal)
        if (plugin.gameManager.currentState == GameState.INGAME) {

            // Si el jugador es un asesino, realizamos limpieza física
            if (plugin.gameManager.esAsesino(uuid)) {

                // Limpiar partículas y efectos visuales del asesino
                plugin.asesinoManager.removerAsesino(player)

                // Lo sacamos de la lista activa de la partida
                plugin.gameManager.asesinosUUIDs.remove(uuid)

                // Si era el último asesino, la partida termina y ganan los supervivientes
                if (plugin.gameManager.asesinosUUIDs.isEmpty()) {
                    plugin.gameManager.stateController.endGame("game.killer-disconnected", false)
                }
            } else {
                // Si era superviviente, lo forzamos a morir/desconectarse para checar victoria
                plugin.gameManager.playerController.handlePlayerDeath(player)
            }
        }

        // 2. LIMPIEZA DE MEMORIA (Hilo Principal)
        // Quitamos al jugador de todos los mapas y sets para evitar Memory Leaks
        plugin.gameManager.asesinosUUIDs.remove(uuid)

        // Si justo era el asesino trakeado, lo limpiamos
        if (plugin.gameManager.currentAsesinoUUID == uuid) {
            plugin.gameManager.currentAsesinoUUID = null
        }

        plugin.combatManager.removePlayerData(uuid)
        plugin.scoreboardManager.removePlayer(player)
        plugin.ambientManager.stopAmbience(player) // Importante detener sonidos

        // 3. PERSISTENCIA DE DATOS (Delegada a Managers)
        plugin.statsManager.unloadPlayer(uuid)

        // Limpieza de LuckPerms (Asíncrona)
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                if (plugin.server.pluginManager.isPluginEnabled("LuckPerms")) {
                    val lp = net.luckperms.api.LuckPermsProvider.get()
                    lp.userManager.modifyUser(uuid) { user ->
                        user.data().clear { node -> node is net.luckperms.api.node.types.PrefixNode }
                    }
                }
            } catch (e: Exception) {
                // Ignorar errores de LP si falla la conexión
            }
        }

        // Guardar datos del perfil (Estamina, lenguaje, etc)
        plugin.playerDataManager.saveConfigSync()
        plugin.playerDataManager.removeData(uuid)
    }
}
