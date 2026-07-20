package liric.mistaken.utils.proxy

import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import pumpking.lib.color.ColorTranslator

object BungeeUtils {

    fun sendToServer(plugin: Mistaken, player: Player, serverName: String) {
        try {
            val b = ByteArrayOutputStream()
            val out = DataOutputStream(b)

            out.writeUTF("Connect")
            out.writeUTF(serverName)

            player.scheduler.run(plugin, { _ ->
                player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray())
            }, null)

        } catch (e: Exception) {
            plugin.componentLogger.error(ColorTranslator.translate("[ERROR] [Proxy] Failed to send ${player.name} to proxy: ${e.message}"))
        }
    }
}
