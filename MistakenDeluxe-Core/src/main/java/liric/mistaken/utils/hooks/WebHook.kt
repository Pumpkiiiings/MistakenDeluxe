package liric.mistaken.utils.hooks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * [LIRIC-MISTAKEN 2.0]
 * WebHook: Integración Webhook para Discord.
 *
 * FIX #3: The original used a CoroutineScope that was never cancelled via Mistaken.onDisable(),
 * and HttpClient was never closed — both leaking resources on shutdown.
 * Now uses a dedicated SupervisorJob so shutdown() can cancel precisely this scope.
 *
 * FIX #16: JSON was built via string interpolation. escape() is now more thorough,
 * covering all JSON control characters (U+0000–U+001F) in addition to \, ", \n, \r.
 */
class WebHook(private val plugin: Mistaken) {

    // FIX #3: Use a dedicated job so shutdown() can cancel only WebHook coroutines
    // without affecting global scopes like PumpkingTask.ioScope.
    private val webhookJob = SupervisorJob()
    private val discordScope = CoroutineScope(Dispatchers.IO + webhookJob)

    // HttpClient is thread-safe and should be reused (avoids repeated SSL handshakes).
    // FIX #3: Now closed in shutdown() to release native TLS/socket resources.
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * Envía el embed de inicio de juego.
     */
    fun sendGameStart(mapa: String, modo: String, survivors: List<Player>, killer: Player) {
        val webhookUrl = getWebhookUrl() ?: return

        val survivorsText = if (survivors.isEmpty()) {
            "Esperando jugadores..."
        } else {
            survivors.joinToString("\\n") { it.name }
        }

        val json = buildJsonPayload(
            title = "🎮 ¡JUEGO INICIADO!",
            color = 65280,
            fields = listOf(
                jsonField("🗺️ Mapa", mapa, inline = true),
                jsonField("🕹️ Modo", modo, inline = true),
                jsonField("🩸 Killer", "**${killer.name.escape()}**", inline = false),
                jsonField("👥 Survivors (${survivors.size})", "```\\n${survivorsText.escape()}\\n```", inline = false)
            ),
            footer = "Mistaken Tracking • LIRIC-MISTAKEN 2.0"
        )

        dispatch(webhookUrl, json)
    }

    /**
     * Envía el embed de fin de juego.
     */
    fun sendGameEnd(mapa: String, ganador: String, razon: String, survivorsNames: List<String>) {
        val webhookUrl = getWebhookUrl() ?: return

        val survivorsText = if (survivorsNames.isEmpty()) {
            "Nadie escapó..."
        } else {
            survivorsNames.joinToString("\\n")
        }

        val json = buildJsonPayload(
            title = "🏁 ¡PARTIDA TERMINADA!",
            description = "**Resultado:** ${razon.escape()}",
            color = 16711680,
            fields = listOf(
                jsonField("🗺️ Mapa", mapa, inline = true),
                jsonField("🏆 Ganador", "**${ganador.escape()}**", inline = true),
                jsonField("🚪 Sobrevivieron", "```\\n${survivorsText.escape()}\\n```", inline = false)
            ),
            footer = "Mistaken Tracking • Sesión finalizada"
        )

        dispatch(webhookUrl, json)
    }

    // --- Internal helpers ---

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
                // Silent: avoids console spam if Discord is unreachable
                plugin.logger.fine("[Discord] Dispatch failed: ${e.message}")
            }
        }
    }

    /**
     * FIX #16: Build JSON via explicit field construction rather than raw string interpolation.
     * Values are always escaped so user-controlled strings (map names, player names, reasons)
     * cannot break the JSON structure or inject unexpected keys.
     */
    private fun buildJsonPayload(
        title: String,
        description: String? = null,
        color: Int,
        fields: List<String>,
        footer: String
    ): String {
        val sb = StringBuilder()
        sb.append("{\"embeds\":[{")
        sb.append("\"title\":\"${title.escape()}\",")
        if (description != null) sb.append("\"description\":\"${description.escape()}\",")
        sb.append("\"color\":$color,")
        sb.append("\"fields\":[${fields.joinToString(",")}],")
        sb.append("\"footer\":{\"text\":\"${footer.escape()}\"},")
        sb.append("\"timestamp\":\"${Instant.now()}\"")
        sb.append("}]}")
        return sb.toString()
    }

    private fun jsonField(name: String, value: String, inline: Boolean): String {
        return "{\"name\":\"${name.escape()}\",\"value\":\"${value.escape()}\",\"inline\":$inline}"
    }

    private fun getWebhookUrl(): String? {
        val url = plugin.config.getString("discord.webhooks.game-tracker")
        return if (url.isNullOrEmpty() || url.contains("URL_AQUI")) null else url
    }

    /**
     * FIX #16: Full JSON string escaping.
     * Covers backslash, double-quote, newlines, carriage returns, and all
     * JSON control characters (U+0000–U+001F) that would produce invalid JSON.
     */
    private fun String.escape(): String {
        val sb = StringBuilder(length)
        for (c in this) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"'  -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    // Control characters: encode as \uXXXX
                    sb.append("\\u%04x".format(c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }

    /**
     * FIX #3: Cancels all pending dispatch coroutines and closes the HttpClient,
     * releasing TLS sessions and socket resources.
     */
    fun shutdown() {
        webhookJob.cancel()
        httpClient.close()
    }
}