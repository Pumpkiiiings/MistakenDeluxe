package liric.mistaken.network

import org.bukkit.entity.Player

interface NetworkProvider {
    /**
     * Connects to the network (e.g. registers channels or connects to Redis).
     */
    fun connect()

    /**
     * Disconnects from the network cleanly.
     */
    fun disconnect()

    /**
     * Publishes the state of this specific GAME_SERVER to the rest of the network.
     */
    fun publishState(payload: ServerStatePayload)

    /**
     * Sends a player to another server via Proxy.
     */
    fun sendPlayer(player: Player, serverName: String)
}
