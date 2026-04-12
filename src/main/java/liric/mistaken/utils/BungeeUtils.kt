package liric.mistaken.utils

import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object BungeeUtils {

    // Registra el canal en el onEnable() de tu Mistaken.kt:
    // server.messenger.registerOutgoingPluginChannel(this, "BungeeCord")

    fun sendToServer(plugin: Mistaken, player: Player, serverName: String) {
        try {
            val b = ByteArrayOutputStream()
            val out = DataOutputStream(b)

            out.writeUTF("Connect")
            out.writeUTF(serverName)

            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray())
        } catch (e: Exception) {
            plugin.componentLogger.error(plugin.mm.deserialize("<red>Error al enviar al jugador al proxy: ${e.message}"))
        }
    }
}
