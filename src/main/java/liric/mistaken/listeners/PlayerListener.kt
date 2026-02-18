package liric.mistaken.listeners

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
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
 * PlayerListener: Gestión de entradas, salidas y estados físicos.
 * Optimizado para Paper 1.21.4 con teletransportación asíncrona.
 */
class PlayerListener(private val plugin: Mistaken) : Listener {

    private val listenerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 1. CARGA DE DATOS ASÍNCRONA (Cero impacto en TPS)
        listenerScope.launch {
            plugin.statsManager.loadStats(uuid, player.name)
            plugin.playerDataManager.loadPlayerData(player)

            // Una vez cargados los datos, añadimos al Scoreboard en el hilo principal
            withContext(Dispatchers.Main) {
                plugin.scoreboardManager.addPlayer(player)
            }
        }

        // 2. LÓGICA DE ESTADO DE PARTIDA
        val currentState = plugin.gameManager.currentState

        // Usamos la propiedad isJoinable que añadimos al Enum anteriormente
        if (!currentState.isJoinable) {
            // Si el juego ya empezó, verificamos si es el asesino volviendo
            if (!plugin.gameManager.esAsesino(uuid)) {
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage(plugin.messageConfig.getMessage(player, "game.join-as-spectator"))
            } else {
                player.sendMessage(plugin.messageConfig.getMessage(player, "killer.rejoin-msg"))
                // Aquí podrías añadir lógica para devolverle sus items de asesino
            }
        } else {
            // Lobby / Voting: Resetear al jugador
            resetPlayerStatus(player)

            // Teleport Asíncrono (Paper API) - No laguea el servidor al cargar el chunk
            plugin.lobbyLocation?.let { loc ->
                player.teleportAsync(loc)
            }
        }
    }

    /**
     * El PlayerQuitListener ya lo teníamos por separado, pero si prefieres
     * tenerlo aquí centralizado, esta es la versión ultra-limpia:
     */
    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // Limpieza de datos en memoria (Main Thread)
        plugin.gameManager.removePlayerData(uuid)
        plugin.scoreboardManager.removePlayer(player)
        plugin.playerDataManager.removeData(uuid)

        // Persistencia (Async)
        listenerScope.launch {
            plugin.statsManager.unloadPlayer(uuid)
        }
    }

    /**
     * Limpia al jugador totalmente usando la API de Atributos de 1.21.4.
     */
    private fun resetPlayerStatus(player: Player) {
        player.gameMode = GameMode.SURVIVAL
        player.isSwimming = false
        player.visualFire = false // Propiedad de Paper/1.21+

        // Atributos con Safe Call de Kotlin
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.health = 20.0
        player.foodLevel = 20
        player.exp = 0f
        player.level = 0
        player.isGlowing = false

        // Limpieza de inventario y pociones eficiente
        player.inventory.clear()
        player.inventory.armorContents = null

        // Usamos una colección inmutable para evitar ConcurrentModificationException
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        // Reset de velocidad por si acaso algún asesino dejó debuffs
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
    }

    /**
     * Limpiar scope al desactivar el plugin
     */
    fun shutdown() {
        listenerScope.cancel()
    }
}
