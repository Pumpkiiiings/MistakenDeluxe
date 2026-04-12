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
import java.util.function.Consumer

/**
 * [LIRIC-MISTAKEN 2.0]
 * PlayerListener: Gestión de ciclo de vida adaptada a Sesiones (Multiarena/Velocity).
 */
class PlayerListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 1. SINCRONIZACIÓN INICIAL
        plugin.musicManager.syncPlayer(player)

        // 2. CARGA DE DATOS ASÍNCRONA
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            plugin.statsManager.loadStats(uuid, player.name)
            plugin.playerDataManager.loadPlayerData(player)

            plugin.server.globalRegionScheduler.execute(plugin) {
                if (player.isOnline) {
                    plugin.scoreboardManager.addPlayer(player)
                }
            }
        }

        // 3. DETERMINAR ENTRADA SEGÚN EL MODO DE RED
        val serverMode = plugin.serverMode // "MULTIARENA" o "VELOCITY"

        if (serverMode == "VELOCITY") {
            // 🔥 MODO NETWORK: El jugador entra directo a la sesión
            val session = plugin.sessionManager.activeSessions.values.firstOrNull()
                ?: plugin.sessionManager.createSession("Cargando...")

            plugin.sessionManager.joinSession(player, session.sessionId)
            handleGameEntry(player, session)

        } else {
            // 🔥 MODO MULTIARENA: El jugador entra al LOBBY primero
            resetPlayerStatus(player)

            // Lo hacemos invisible para los que están jugando en arenas
            plugin.isolationManager.updateVisibility(player)

            plugin.lobbyLocation?.let { loc ->
                player.teleportAsync(loc).thenAccept { success ->
                    if (success) {
                        val welcome = plugin.messageConfig.getMessage(player, "lobby.welcome")
                        if (!welcome.toString().isEmpty()) player.sendMessage(welcome)
                    }
                }
            }
        }
    }

    /**
     * Lógica para cuando un jugador entra a una sesión específica (sea en Velocity o Multiarena).
     */
    private fun handleGameEntry(player: Player, session: liric.mistaken.game.GameSession) {
        val uuid = player.uniqueId
        val state = session.currentState

        if (state != GameState.LOBBY && state != GameState.VOTING && state != GameState.BREAK) {
            // La partida ya empezó o está terminando
            if (!session.esAsesino(uuid)) {
                // Es un espectador nuevo
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage(plugin.messageConfig.getMessage(player, "game.join-as-spectator"))

                val killer = session.getCurrentAsesino()
                if (killer != null) {
                    player.teleportAsync(killer.location)
                }
            } else {
                // El asesino se reconectó
                player.sendMessage(plugin.messageConfig.getMessage(player, "killer.rejoin-msg"))
                plugin.asesinoManager.getAsesinoDelJugador(player)?.equipar(player)
            }
        } else {
            // Aún están en Lobby/Votación de la arena
            resetPlayerStatus(player)

            // Teleport al spawn de la arena si existe, si no al centro
            val spawn = session.plugin.arenaManager.getArena(session.currentMapName)?.survivorSpawns?.firstOrNull()
            if (spawn != null) player.teleportAsync(spawn)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Limpiamos su rastro de las sesiones
        plugin.sessionManager.leaveSession(event.player)
        plugin.scoreboardManager.removePlayer(event.player)
    }

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

        if (player.gameMode == GameMode.SPECTATOR) player.spectatorTarget = null

        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4)

        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 4.0
    }
}
