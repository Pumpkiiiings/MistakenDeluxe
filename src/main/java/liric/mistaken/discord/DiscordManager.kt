package liric.mistaken.discord

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * [LIRIC-MISTAKEN 2.0]
 * DiscordManager: Integración Webhook.
 * FIX: Solucionado el problema de saltos de línea (\n) y escape de comillas en JSON.
 */
class DiscordManager(private val plugin: Mistaken) {

    // Cliente HTTP optimizado (Reutilizable para evitar handshake SSL repetitivo)
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val discordScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Envía el embed de inicio de juego.
     */
    fun sendGameStart(mapa: String, modo: String, survivors: List<Player>, killer: Player) {
        val webhookUrl = getWebhookUrl() ?: return

        // 1. Formateamos la lista de supervivientes
        // Usamos joinToString con "\n" explícito para que el JSON lo lea como salto de línea.
        val survivorsText = if (survivors.isEmpty()) {
            "Esperando jugadores..."
        } else {
            survivors.joinToString("\\n") { it.name }
        }

        // 2. Construimos el JSON escapando los valores peligrosos
        val json = """
        {
          "embeds": [{
            "title": "🎮 ¡JUEGO INICIADO!",
            "color": 65280,
            "fields": [
              { "name": "🗺️ Mapa", "value": "${mapa.escape()}", "inline": true },
              { "name": "🕹️ Modo", "value": "${modo.escape()}", "inline": true },
              { "name": "🩸 Asesino", "value": "**${killer.name.escape()}**", "inline": false },
              { "name": "👥 Supervivientes (${survivors.size})", "value": "```\n${survivorsText.escape()}\n```", "inline": false }
            ],
            "footer": { "text": "Mistaken Tracking • LIRIC-MISTAKEN 2.0" },
            "timestamp": "${Instant.now()}"
          }]
        }
        """.trimIndent()

        dispatch(webhookUrl, json)
    }

    /**
     * Envía el embed de fin de juego.
     */
    fun sendGameEnd(mapa: String, ganador: String, razon: String, survivorsNames: List<String>) {
        val webhookUrl = getWebhookUrl() ?: return

        // Formateamos la lista de nombres (String)
        val survivorsText = if (survivorsNames.isEmpty()) {
            "Nadie escapó..."
        } else {
            survivorsNames.joinToString("\\n")
        }

        val json = """
        {
          "embeds": [{
            "title": "🏁 ¡PARTIDA TERMINADA!",
            "description": "**Resultado:** ${razon.escape()}",
            "color": 16711680,
            "fields": [
              { "name": "🗺️ Mapa", "value": "${mapa.escape()}", "inline": true },
              { "name": "🏆 Ganador", "value": "**${ganador.escape()}**", "inline": true },
              { "name": "🚪 Sobrevivieron", "value": "```\n${survivorsText.escape()}\n```", "inline": false }
            ],
            "footer": { "text": "Mistaken Tracking • Sesión finalizada" },
            "timestamp": "${Instant.now()}"
          }]
        }
        """.trimIndent()

        dispatch(webhookUrl, json)
    }

    private fun dispatch(urlStr: String, json: String) {
        discordScope.launch {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mistaken-Tracker")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() !in 200..299) {
                    plugin.logger.warning("[Discord] Error ${response.statusCode()}: ${response.body()}")
                }
            } catch (e: Exception) {
                // Silencioso para no spammear consola si se va el internet
            }
        }
    }

    /**
     * Obtiene y valida la URL del config.
     */
    private fun getWebhookUrl(): String? {
        val url = plugin.config.getString("discord.webhooks.game-tracker")
        return if (url.isNullOrEmpty() || url.contains("URL_AQUI")) null else url
    }

    /**
     * 🔥 FUNCIÓN CRÍTICA: Limpia el texto para que sea JSON válido.
     * Evita que comillas (") o barras (\) rompan el formato.
     */
    private fun String.escape(): String {
        return this.replace("\\", "\\\\") // Escapar barras invertidas
            .replace("\"", "\\\"") // Escapar comillas dobles
            .replace("\n", "\\n")  // Escapar saltos de línea reales
            .replace("\r", "")
    }

    fun shutdown() {
        discordScope.cancel()
    }
}
