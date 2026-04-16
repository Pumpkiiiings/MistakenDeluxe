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

/**
 * [LIRIC-MISTAKEN 2.0]
 * PlayerListener: Gestión de ciclo de vida optimizada para Folia / Paper.
 * FIX: Lógica de red adaptada a Managers Nulables para no consumir RAM en NETWORK_LOBBY.
 */
class PlayerListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // 🔥 Ocultamos el mensaje por defecto de Minecraft para inmersión
        event.joinMessage(null)

        // 1. SINCRONIZACIÓN INICIAL (Manager Core - Siempre existe)
        plugin.musicManager.syncPlayer(player)

        // 2. CARGA DE DATOS ASÍNCRONA
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            plugin.statsManager.loadStats(uuid, player.name)
            plugin.playerDataManager.loadPlayerData(player)

            // Folia-Ready: Volver al hilo principal de la ENTIDAD (no al global)
            player.scheduler.run(plugin, { _ ->
                if (player.isOnline) {
                    plugin.scoreboardManager.addPlayer(player)
                }
            }, null)
        }

        // 3. LÓGICA DE RED (Network Lobby vs Game Server vs Multiarena)
        val serverMode = plugin.serverMode

        if (serverMode == "NETWORK_LOBBY") {
            // 🔥 LOBBY PRINCIPAL: Retornamos aquí. Ningún manager de juego se tocará.
            resetPlayerStatus(player)
            plugin.lobbyLocation?.let { player.teleportAsync(it) }
            return
        }

        // 🔥 Si llegamos aquí, ES UN SERVIDOR DE JUEGO.
        // Usamos variables locales seguras invocando los managers que ya sabemos que se iniciaron.
        if (serverMode == "GAME_SERVER") {
            val maxPlayers = plugin.config.getInt("settings.max-players-per-arena", 10)
            val sessionManager = plugin.sessionManager ?: return

            var targetSession = sessionManager.activeSessions.values.firstOrNull {
                (it.currentState == GameState.LOBBY || it.currentState == GameState.VOTING || it.currentState == GameState.BREAK) && it.getPlayers().size < maxPlayers
            }

            if (targetSession == null) {
                targetSession = sessionManager.createSession("Votando...")
            }

            sessionManager.joinSession(player, targetSession.sessionId)

            // Teleport Async, luego actualizar visibilidad
            plugin.lobbyLocation?.let { preLobby ->
                player.teleportAsync(preLobby).thenAccept {
                    plugin.isolationManager?.updateVisibility(player)
                }
            }

            val minPlayers = plugin.config.getInt("settings.min-players", 4)
            if (targetSession.getPlayers().size >= minPlayers && targetSession.currentState == GameState.LOBBY) {
                targetSession.stateController.startBreakProcess()
            }
            return
        }

        // MULTIARENA
        resetPlayerStatus(player)
        plugin.isolationManager?.updateVisibility(player)

        plugin.lobbyLocation?.let { loc ->
            player.teleportAsync(loc).thenAccept { success ->
                // Verificamos que siga online después del teleport asíncrono
                if (success && player.isOnline) {
                    val welcome = plugin.messageConfig.getMessage(player, "lobby.welcome")
                    if (welcome.toString().isNotEmpty()) player.sendMessage(welcome)
                }
            }
        }
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
