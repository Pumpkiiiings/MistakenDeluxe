package liric.mistaken.network.providers

import com.google.common.io.ByteStreams
import liric.mistaken.network.NetworkManager
import liric.mistaken.network.NetworkProvider
import liric.mistaken.network.ServerStatePayload
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener

class BungeeProvider(private val manager: NetworkManager) : NetworkProvider, PluginMessageListener {

    private val channelName = "BungeeCord"
    private val subChannel = "MistakenSync"

    override fun connect() {
        manager.plugin.server.messenger.registerOutgoingPluginChannel(manager.plugin, channelName)
        manager.plugin.server.messenger.registerIncomingPluginChannel(manager.plugin, channelName, this)
    }

    override fun disconnect() {
        manager.plugin.server.messenger.unregisterOutgoingPluginChannel(manager.plugin, channelName)
        manager.plugin.server.messenger.unregisterIncomingPluginChannel(manager.plugin, channelName, this)
    }

    override fun publishState(payload: ServerStatePayload) {
        val player = org.bukkit.Bukkit.getOnlinePlayers().firstOrNull() ?: return

        val out = ByteStreams.newDataOutput()
        out.writeUTF("Forward")
        out.writeUTF("ONLINE") // Send to all servers
        out.writeUTF(subChannel)

        val messageData = payload.toJson().toByteArray(Charsets.UTF_8)
        out.writeShort(messageData.size)
        out.write(messageData)

        player.sendPluginMessage(manager.plugin, channelName, out.toByteArray())
    }

    override fun sendPlayer(player: Player, serverName: String) {
        liric.mistaken.utils.misc.BungeeUtils.sendToServer(manager.plugin, player, serverName)
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != channelName) return

        val input = ByteStreams.newDataInput(message)
        val sub = input.readUTF()
        if (sub == subChannel) {
            val length = input.readShort()
            val messageBytes = ByteArray(length.toInt())
            input.readFully(messageBytes)

            val json = String(messageBytes, Charsets.UTF_8)
            ServerStatePayload.fromJson(json)?.let {
                manager.handleIncomingPayload(it)
            }
        }
    }
}
