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
 * PlayerListener: GestiÃ³n de ciclo de vida adaptada a Sesiones (Multiarena / Pre-Lobbys).
 * FIX: Chat silencioso para inmersiÃ³n total en Network/Multiarena.
 */
class PlayerListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // ðŸ”¥ Ocultamos el mensaje por defecto de Minecraft para no romper la inmersiÃ³n del lobby aislado
        event.joinMessage(null)

        // 1. SINCRONIZACIÃ“N INICIAL
        plugin.musicManager.syncPlayer(player)

        // 2. CARGA DE DATOS ASÃNCRONA
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            plugin.statsManager.loadStats(uuid, player.name)
            plugin.playerDataManager.loadPlayerData(player)

            plugin.server.globalRegionScheduler.execute(plugin) {
                if (player.isOnline) {
                    plugin.scoreboardManager.addPlayer(player)
                }
            }
        }

        // 3. LÃ“GICA DE RED (Network Lobby vs Game Server vs Multiarena)
        val serverMode = plugin.serverMode

        if (serverMode == "NETWORK_LOBBY") {
            // ðŸ”¥ LOBBY PRINCIPAL: Nadie juega, solo compran en la tienda.
            resetPlayerStatus(player)
            plugin.lobbyLocation?.let { player.teleportAsync(it) }
            return
        }

        if (serverMode == "GAME_SERVER") {
            // ðŸ”¥ PRE-LOBBY DE CRISTAL: Buscamos una sesiÃ³n esperando o creamos una nueva
            val maxPlayers = plugin.config.getInt("settings.max-players-per-arena", 10)

            var targetSession = plugin.sessionManager.activeSessions.values.firstOrNull {
                (it.currentState == GameState.LOBBY || it.currentState == GameState.VOTING || it.currentState == GameState.BREAK) && it.getPlayers().size < maxPlayers
            }

            if (targetSession == null) {
                targetSession = plugin.sessionManager.createSession("Votando...")
            }

            plugin.sessionManager.joinSession(player, targetSession.id)

            // Los mandamos fÃ­sicamente al Pre-Lobby de cristal (lobbyLocation)
            plugin.lobbyLocation?.let { preLobby ->
                player.teleportAsync(preLobby).thenAccept {
                    // ðŸ”¥ LA MAGIA: Oculta a los de otras sesiones que estÃ©n en la misma caja de cristal
                    plugin.isolationManager.updateVisibility(player)
                }
            }

            // Checamos si la sesiÃ³n ya puede empezar el contador (Ej: Llegaron a 4 jugadores)
            val minPlayers = plugin.config.getInt("settings.min-players", 4)
            if (targetSession.getPlayers().size >= minPlayers && targetSession.currentState == GameState.LOBBY) {
                targetSession.stateController.startBreakProcess()
            }
            return
        }

        // MULTIARENA (Todos entran al lobby general hasta que entren por comandos a una sesiÃ³n)
        resetPlayerStatus(player)
        plugin.isolationManager.updateVisibility(player)

        plugin.lobbyLocation?.let { loc ->
            player.teleportAsync(loc).thenAccept { success ->
                if (success) {
                    val welcome = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "lobby.welcome")
                    if (welcome.toString().isNotEmpty()) player.sendMessage(welcome)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // ðŸ”¥ Ocultamos el mensaje por defecto de Minecraft al salir
        event.quitMessage(null)

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

