package liric.mistaken.network

import liric.mistaken.Mistaken
import liric.mistaken.network.providers.BungeeProvider
import liric.mistaken.network.providers.RedisProvider
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

class NetworkManager(val plugin: Mistaken) {

    val servers = ConcurrentHashMap<String, ServerStatePayload>()
    var provider: NetworkProvider? = null
    
    private var taskID = -1

    init {
        val type = plugin.config.getString("network.provider", "BUNGEE")?.uppercase()
        provider = if (type == "REDIS") {
            RedisProvider(this)
        } else {
            BungeeProvider(this)
        }

        provider?.connect()

        if (plugin.serverMode == "GAME_SERVER" || plugin.serverMode == "MULTIARENA") {
            // Heartbeat: Publish state every 3 seconds
            taskID = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
                publishLocalState()
            }, 20L, 60L).taskId
        }

        if (plugin.serverMode == "NETWORK_LOBBY") {
            // Cleanup stale servers every 10 seconds
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
                val now = System.currentTimeMillis()
                servers.entries.removeIf { now - it.value.lastUpdate > 10000L }
            }, 100L, 200L)
        }
    }

    fun shutdown() {
        if (taskID != -1) Bukkit.getScheduler().cancelTask(taskID)
        provider?.disconnect()
    }

    private fun publishLocalState() {
        // Find best local session state
        val activeSessions = plugin.sessionManager.activeSessions.values
        val maxPlayers = plugin.config.getInt("settings.max-players-per-arena", 10)
        
        // Find the most appropriate session to advertise
        val session = activeSessions.firstOrNull { it.currentState == liric.mistaken.game.enums.GameState.LOBBY || it.currentState == liric.mistaken.game.enums.GameState.VOTING }
            ?: activeSessions.firstOrNull()

        val state = session?.currentState?.name ?: "LOBBY"
        val players = session?.getPlayers()?.size ?: 0
        
        val serverName = plugin.config.getString("network.local-server-name", "arena-1") ?: "arena-1"

        provider?.publishState(ServerStatePayload(serverName, state, players, maxPlayers))
    }

    fun handleIncomingPayload(payload: ServerStatePayload) {
        payload.lastUpdate = System.currentTimeMillis()
        servers[payload.serverName] = payload
    }

    fun getBestAvailableServer(): String? {
        val validServers = servers.values.filter { 
            (it.state == "LOBBY" || it.state == "VOTING") && it.players < it.maxPlayers 
        }
        
        // Sort by most full (to fill arenas faster)
        return validServers.sortedByDescending { it.players }.firstOrNull()?.serverName
    }

    fun joinBestServer(player: Player) {
        val best = getBestAvailableServer()
        if (best != null) {
            liric.mistaken.config.engine.core.MessageService.send(player, "network.connecting", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("server", best))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
            provider?.sendPlayer(player, best)
        } else {
            liric.mistaken.config.engine.core.MessageService.send(player, "network.no-arenas")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }
}
