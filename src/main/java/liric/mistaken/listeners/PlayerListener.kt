package liric.mistaken.listeners

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
 * PlayerListener: Gestión de ciclo de vida del jugador.
 * FIX: Thread-Safety y uso correcto de Paper API.
 */
class PlayerListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 🔥 FIX: Sincronizar música inmediatamente
        plugin.musicManager.syncPlayer(player)

        // 1. CARGA DE DATOS ASÍNCRONA (Base de Datos)
        // Usamos AsyncScheduler para no bloquear el login
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            // Cargar stats y perfil
            plugin.statsManager.loadStats(uuid, player.name)
            plugin.playerDataManager.loadPlayerData(player)

            // 2. REGRESO AL HILO PRINCIPAL (Para Scoreboard y Bukkit API)
            plugin.server.globalRegionScheduler.execute(plugin) {
                if (player.isOnline) {
                    plugin.scoreboardManager.addPlayer(player)
                }
            }
        }

        // 3. LÓGICA DE ESTADO DE PARTIDA (Hilo Principal - Evento Síncrono)
        val currentState = plugin.gameManager.currentState

        if (currentState != GameState.LOBBY && currentState != GameState.VOTING) {
            // Juego en curso -> Espectador o Reconnect
            if (!plugin.gameManager.esAsesino(uuid)) {
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage(plugin.messageConfig.getMessage(player, "game.join-as-spectator"))

                // Teletransportar al asesino o centro del mapa si es posible
                val killer = plugin.gameManager.getCurrentAsesino()
                if (killer != null) {
                    player.teleportAsync(killer.location)
                }
            } else {
                player.sendMessage(plugin.messageConfig.getMessage(player, "killer.rejoin-msg"))
                // Restaurar kit de asesino (delegamos al manager)
                plugin.asesinoManager.getAsesinoDelJugador(player)?.equipar(player)
            }
        } else {
            // Lobby -> Reset y Teleport
            resetPlayerStatus(player)

            plugin.lobbyLocation?.let { loc ->
                player.teleportAsync(loc).thenAccept { success ->
                    if (success) {
                        // Mensaje de bienvenida limpio
                        val welcome = plugin.messageConfig.getMessage(player, "lobby.welcome")
                        player.sendMessage(welcome)
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // La lógica pesada de Quit ya está en PlayerQuitListener.kt
        // Aquí solo mantenemos limpieza visual inmediata si fuera necesaria.
    }

    /**
     * Limpia al jugador totalmente.
     */
    private fun resetPlayerStatus(player: Player) {
        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f
        player.exp = 0f
        player.level = 0
        player.isGlowing = false
        player.isSwimming = false
        player.isVisualFire = false
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f

        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4)

        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        // Reset de Atributos Críticos
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 4.0 // 1.9+ Attack Speed normal
    }
}
