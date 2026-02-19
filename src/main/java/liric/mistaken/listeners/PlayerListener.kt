package liric.mistaken.listeners

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.mainThread // 1. IMPORTANTE: Importar nuestra extensión
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * [LIRIC-MISTAKEN 2.0]
 * PlayerListener: Gestión de ciclo de vida del jugador.
 *
 * Optimizaciones:
 * - Cambio de Dispatchers.Main a plugin.mainThread (Fixes IllegalStateException).
 * - Carga asíncrona de datos de DB y PlayerDataManager.
 * - Uso de teleportAsync (Paper API) para evitar lag de carga de chunks.
 */
class PlayerListener(private val plugin: Mistaken) : Listener {

    // Scope para tareas asíncronas de este listener
    private val listenerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 1. CARGA DE DATOS ASÍNCRONA (Base de Datos + Archivos)
        listenerScope.launch {
            // Estas operaciones corren en el hilo IO (No laguean)
            plugin.playerStatsManager.addStat(uuid, player.name, "kills") // Ejemplo de inicialización si no existe
            plugin.playerDataManager.loadPlayerData(player)

            // 2. REGRESO AL HILO PRINCIPAL (Para tocar la API de Bukkit)
            // Reemplazamos Dispatchers.Main por plugin.mainThread
            withContext(plugin.mainThread) {
                if (player.isOnline) {
                    plugin.scoreboardManager.addPlayer(player)
                }
            }
        }

        // 3. LÓGICA DE ESTADO DE PARTIDA (Hilo Principal)
        val currentState = plugin.gameManager.currentState

        // Verificamos si la partida permite entrar
        if (currentState != GameState.LOBBY && currentState != GameState.VOTING) {

            // Si el juego ya empezó, el jugador entra como espectador
            // (A menos que sea el asesino que se desconectó y volvió)
            if (!plugin.gameManager.esAsesino(uuid)) {
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage(plugin.messageConfig.getMessage(player, "game.join-as-spectator"))
            } else {
                player.sendMessage(plugin.messageConfig.getMessage(player, "killer.rejoin-msg"))
                // Aquí podrías añadir lógica para restaurar su kit de asesino
            }
        } else {
            // Estamos en Lobby: Resetear jugador y mandarlo al spawn
            resetPlayerStatus(player)

            // Teletransportación Asíncrona (Paper 1.21.4)
            // Esto es mucho mejor que player.teleport() porque no congela el servidor
            plugin.lobbyLocation?.let { loc ->
                player.teleportAsync(loc).thenAccept { success ->
                    if (success) {
                        player.sendMessage(plugin.mm.deserialize("<green>¡Conectado al Lobby!"))
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // Limpieza inmediata en memoria (HILO PRINCIPAL)
        plugin.gameManager.removePlayerData(uuid)
        plugin.scoreboardManager.removePlayer(player)
        plugin.playerDataManager.removeData(uuid)

        // Guardado de datos final (ASÍNCRONO)
        listenerScope.launch {
            // plugin.playerStatsManager.saveEverything(uuid)
        }
    }

    /**
     * Limpia al jugador totalmente usando la API moderna de Atributos.
     */
    private fun resetPlayerStatus(player: Player) {
        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.exp = 0f
        player.level = 0
        player.isGlowing = false
        player.isSwimming = false
        player.setVisualFire(false)

        // Limpieza de inventario (Paper es muy eficiente aquí)
        player.inventory.clear()
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        // Reset de Atributos (Usando los nuevos nombres de la 1.21.4)
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 4.0
    }

    /**
     * Cancela todas las tareas pendientes de este listener al apagar el plugin.
     */
    fun shutdown() {
        listenerScope.cancel()
    }
}
