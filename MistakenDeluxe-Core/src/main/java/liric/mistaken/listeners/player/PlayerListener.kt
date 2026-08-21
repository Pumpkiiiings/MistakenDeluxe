package liric.mistaken.listeners.player

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
import liric.mistaken.config.engine.core.MessageService
import org.bukkit.event.player.PlayerResourcePackStatusEvent


class PlayerListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        
        event.joinMessage(null)

        
        plugin.musicManager.syncPlayer(player)

        
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            plugin.statsManager.loadStats(uuid, player.name)
            plugin.playerDataManager.loadPlayerData(player)

            plugin.server.globalRegionScheduler.execute(plugin) {
                if (player.isOnline) {
                    plugin.scoreboardManager.addPlayer(player)
                    plugin.nameTagManager.setupPlayer(player)
                }
            }
        }

        
        val serverMode = plugin.serverMode

        if (serverMode == "NETWORK_LOBBY") {
            
            resetPlayerStatus(player)
            plugin.lobbyLocation?.let { player.teleportAsync(it) }
            return
        }

        if (serverMode == "GAME_SERVER") {
            
            val maxPlayers = plugin.config.getInt("settings.max-players-per-arena", 10)

            var targetSession = plugin.sessionManager.activeSessions.values.firstOrNull {
                (it.currentState == GameState.LOBBY || it.currentState == GameState.VOTING || it.currentState == GameState.BREAK) && it.getPlayers().size < maxPlayers
            }

            if (targetSession == null) {
                targetSession = plugin.sessionManager.createSession("Votando...")
            }

            plugin.sessionManager.joinSession(player, targetSession.id)

            
            plugin.lobbyLocation?.let { preLobby ->
                player.teleportAsync(preLobby).thenAccept {
                    
                    plugin.isolationManager.updateVisibility(player)
                }
            }

            
            val minPlayers = plugin.config.getInt("settings.min-players", 4)
            if (targetSession.getPlayers().size >= minPlayers && targetSession.currentState == GameState.LOBBY) {
                targetSession.stateController.startBreakProcess()
            }
            return
        }

        
        resetPlayerStatus(player)
        plugin.isolationManager.updateVisibility(player)

        plugin.lobbyLocation?.let { loc ->
            player.teleportAsync(loc).thenAccept { success ->
                if (success) {
                    val welcome = MessageService.getComponent(player, "lobby.welcome")
                    if (welcome.toString().isNotEmpty()) player.sendMessage(welcome)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        
        event.quitMessage(null)

        plugin.nameTagManager.removePlayer(event.player)
        plugin.sessionManager.leaveSession(event.player)
        plugin.scoreboardManager.removePlayer(event.player)
    }

    companion object {
        fun resetPlayerStatus(player: Player) {
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
            player.getAttribute(Attribute.SCALE)?.baseValue = 1.0

            liric.mistaken.utils.hooks.ObserverHook.setTrueDarkness(player, false)
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerItemDamage(event: org.bukkit.event.player.PlayerItemDamageEvent) {
        event.isCancelled = true
    }

    @EventHandler
    fun onResourcePackStatus(event: PlayerResourcePackStatusEvent) {
        val status = event.status
        if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED ||
            status == PlayerResourcePackStatusEvent.Status.DECLINED ||
            status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            
            
            
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                plugin.musicManager.stopMusicForPlayer(event.player)
            }, 20L) 
        }
    }
}
