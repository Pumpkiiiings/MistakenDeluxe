package liric.mistaken.listeners

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.types.PrefixNode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

/**
 * [LIRIC-MISTAKEN 2.0]
 * PlayerQuitListener: Limpieza profunda y persistencia asíncrona.
 * Optimizado para liberar memoria RAM instantáneamente tras la desconexión.
 */
class PlayerQuitListener(private val plugin: Mistaken) : Listener {

    // Scope para tareas de persistencia (IO)
    private val quitScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 1. LÓGICA DE JUEGO INMEDIATA (Hilo Principal)
        if (plugin.gameManager.currentState == GameState.INGAME) {

            // Si el jugador es un asesino, realizamos limpieza física
            if (plugin.gameManager.esAsesino(uuid)) {

                // Limpiar partículas, efectos y tareas locales del objeto Asesino
                plugin.asesinoManager.getAsesinoDelJugador(player)?.cleanup(player)

                // Si era el último asesino, la partida debe terminar
                // Nota: Verificamos contra 1 porque el jugador aún cuenta en el Set en este milisegundo
                if (plugin.gameManager.asesinosUUIDs.size <= 1) {
                    plugin.gameManager.endGame("game.killer-disconnected", false)
                }
            }
        }

        // 2. LIMPIEZA DE MEMORIA (Hilo Principal)
        // Quitamos al jugador de todos los mapas y sets para evitar Memory Leaks
        plugin.gameManager.removePlayerData(uuid)
        plugin.asesinoManager.removerAsesino(player)
        plugin.scoreboardManager.removePlayer(player)

        // 3. PERSISTENCIA Y PERMISOS (Hilo Secundario / Coroutines)
        // Esto no consume recursos del Tick principal
        quitScope.launch {

            // Guardar estadísticas y remover del caché de la base de datos
            try {
                plugin.statsManager.unloadPlayer(uuid)
            } catch (e: Exception) {
                plugin.logger.warning("No se pudieron guardar las stats de ${player.name}: ${e.message}")
            }

            // Limpieza de LuckPerms (Acceso a DB de LP)
            try {
                limpiarPrefijoLuckPerms(uuid)
            } catch (ignored: Exception) {}

            // Guardar datos del perfil (Estamina, lenguaje, etc)
            plugin.playerDataManager.saveConfigSync()
            plugin.playerDataManager.removeData(uuid)
        }
    }

    /**
     * Limpia los prefijos de LuckPerms de forma asíncrona.
     */
    private fun limpiarPrefijoLuckPerms(uuid: UUID) {
        val lp = LuckPermsProvider.get()
        lp.userManager.modifyUser(uuid) { user ->
            user.data().clear { node -> node is PrefixNode }
        }
    }
}
