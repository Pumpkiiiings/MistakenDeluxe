package liric.mistaken.network.providers

import liric.mistaken.MistakenLib
import liric.mistaken.network.NetworkManager
import liric.mistaken.network.NetworkProvider
import liric.mistaken.network.ServerStatePayload
import org.bukkit.entity.Player
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import java.util.concurrent.Executors

class RedisProvider(private val manager: NetworkManager) : NetworkProvider {

    private var pool: JedisPool? = null
    private val channelName = manager.plugin.config.getString("network.redis.channel", "mistaken:sync") ?: "mistaken:sync"
    private var pubSub: JedisPubSub? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun connect() {
        val host = manager.plugin.config.getString("network.redis.host", "localhost")
        val port = manager.plugin.config.getInt("network.redis.port", 6379)
        val password = manager.plugin.config.getString("network.redis.password", "")

        val poolConfig = JedisPoolConfig()
        poolConfig.maxTotal = 8

        pool = if (password.isNullOrEmpty()) {
            JedisPool(poolConfig, host, port)
        } else {
            JedisPool(poolConfig, host, port, 2000, password)
        }

        executor.submit {
            try {
                pool?.resource.use { jedis ->
                    pubSub = object : JedisPubSub() {
                        override fun onMessage(channel: String?, message: String?) {
                            if (channel == channelName && message != null) {
                                ServerStatePayload.fromJson(message)?.let {
                                    manager.handleIncomingPayload(it)
                                }
                            }
                        }
                    }
                    jedis?.subscribe(pubSub, channelName)
                }
            } catch (e: Exception) {
                MistakenLib.logError(MistakenLib.LogCategory.CORE, "[RedisProvider] Connection failed: ${e.message}")
            }
        }
    }

    override fun disconnect() {
        pubSub?.unsubscribe()
        pool?.close()
        executor.shutdown()
    }

    override fun publishState(payload: ServerStatePayload) {
        try {
            pool?.resource.use { jedis ->
                jedis?.publish(channelName, payload.toJson())
            }
        } catch (e: Exception) {
            // Ignore temporary connection failures
        }
    }

    override fun sendPlayer(player: Player, serverName: String) {
        // Fallback to BungeeCord for sending players since Redis cannot proxy players natively
        liric.mistaken.utils.misc.BungeeUtils.sendToServer(manager.plugin, player, serverName)
    }
}
