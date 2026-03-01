package liric.mistaken.listeners

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 * [LIRIC-MISTAKEN 2.0]
 * PlayerQuitListener: Limpieza profunda y persistencia.
 * FIX: Lógica de juego corregida y guardado de datos seguro.
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

                // Limpiar partículas y efectos visuales
                plugin.asesinoManager.removerAsesino(player)

                // Verificar condición de victoria por abandono
                // Filtramos la lista actual quitando al que se acaba de ir
                val remainingKillers = plugin.gameManager.asesinosUUIDs.filter { it != uuid }

                if (remainingKillers.isEmpty()) {
                    plugin.gameManager.endGame("game.killer-disconnected", false)
                }
            }
        }

        // 2. LIMPIEZA DE MEMORIA (Hilo Principal)
        // Quitamos al jugador de todos los mapas y sets para evitar Memory Leaks
        plugin.gameManager.removePlayerData(uuid)
        plugin.combatManager.removePlayerData(uuid)
        plugin.scoreboardManager.removePlayer(player)
        plugin.ambientManager.stopAmbience(player) // Importante detener sonidos

        // 3. PERSISTENCIA DE DATOS (Delegada a Managers)
        // StatsManager ya tiene su propio scope IO, así que es seguro llamarlo aquí.
        plugin.statsManager.unloadPlayer(uuid)

        // Limpieza de LuckPerms (Si tienes el hook)
        // Usamos runAsync de Paper para no bloquear el quit event
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                if (plugin.server.pluginManager.isPluginEnabled("LuckPerms")) {
                    val lp = net.luckperms.api.LuckPermsProvider.get()
                    lp.userManager.modifyUser(uuid) { user ->
                        user.data().clear { node -> node is net.luckperms.api.node.types.PrefixNode }
                    }
                }
            } catch (e: Exception) {
                // Ignorar errores de LP
            }
        }

        // Guardar datos del perfil (Estamina, lenguaje, etc)
        // saveConfigSync es rápido si solo vuelca memoria a disco
        plugin.playerDataManager.saveConfigSync()
        plugin.playerDataManager.removeData(uuid)
    }
}
