package liric.mistaken.discord

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.logging.Level

/**
 * [LIRIC-MISTAKEN 2.0]
 * DiscordManager: Integración con Webhooks de forma ultra-eficiente.
 * Usa el HttpClient de Java 21 y Coroutines para no afectar los TPS.
 */
class DiscordManager(private val plugin: Mistaken) {

    // Cliente HTTP reutilizable (Optimiza el handshake SSL)
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // Scope para tareas de red
    private val discordScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Envía el embed de inicio de juego de forma asíncrona.
     */
    fun sendGameStart(mapa: String, modo: String, survivors: List<Player>, killer: Player) {
        val webhookUrl = plugin.config.getString("discord.webhooks.game-tracker")
        if (webhookUrl.isNullOrEmpty() || webhookUrl == "URL_AQUI") return

        // joinToString es más rápido que streams en listas pequeñas
        val survivorsList = survivors.joinToString("\\n") { it.name }

        val json = """
        {
          "embeds": [{
            "title": "🎮 ¡JUEGO INICIADO!",
            "color": 15105536,
            "fields": [
              { "name": "🗺️ Mapa", "value": "$mapa", "inline": true },
              { "name": "🕹️ Modo", "value": "$modo", "inline": true },
              { "name": "🩸 Asesino", "value": "**${killer.name}**", "inline": false },
              { "name": "👥 Supervivientes", "value": "```\\n$survivorsList\\n```", "inline": false }
            ],
            "footer": { "text": "Mistaken Tracking • LIRIC-MISTAKEN 2.0" },
            "timestamp": "${Instant.now()}"
          }]
        }
        """.trimIndent()

        dispatch(webhookUrl, json)
    }

    /**
     * Envía el embed de fin de juego de forma asíncrona.
     */
    fun sendGameEnd(mapa: String, ganador: String, razon: String, survivorsNames: List<String>) {
        val webhookUrl = plugin.config.getString("discord.webhooks.game-tracker")
        if (webhookUrl.isNullOrEmpty() || webhookUrl == "URL_AQUI") return

        val survivorsList = survivorsNames.ifEmpty { listOf("Nadie escapó...") }.joinToString("\\n")

        val json = """
        {
          "embeds": [{
            "title": "🏁 ¡PARTIDA TERMINADA!",
            "description": "**Resultado:** $razon",
            "color": 3066993,
            "fields": [
              { "name": "🗺️ Mapa", "value": "$mapa", "inline": true },
              { "name": "🏆 Ganador", "value": "**$ganador**", "inline": true },
              { "name": "🚪 Sobrevivieron", "value": "```\\n$survivorsList\\n```", "inline": false }
            ],
            "footer": { "text": "Mistaken Tracking • Sesión finalizada" },
            "timestamp": "${Instant.now()}"
          }]
        }
        """.trimIndent()

        dispatch(webhookUrl, json)
    }

    /**
     * Realiza la petición POST al webhook en un hilo de E/S secundario.
     */
    private fun dispatch(urlStr: String, json: String) {
        discordScope.launch {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mistaken-Tracker-Liric")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build()

                // Enviamos y esperamos respuesta (en el hilo IO)
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() >= 400) {
                    plugin.logger.warning("[Discord] Error al enviar Webhook (Código: ${response.statusCode()})")
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "[Discord] Fallo en la conexión de red: ${e.message}")
            }
        }
    }

    /**
     * Cancela las peticiones pendientes al apagar el plugin.
     */
    fun shutdown() {
        discordScope.cancel()
    }
}
