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


class WebHook(private val plugin: Mistaken) {

    // FIX #3: Use a dedicated job so shutdown() can cancel only WebHook coroutines
    // without affecting global scopes like MistakenLib.ioScope.
    private val webhookJob = SupervisorJob()
    private val discordScope = CoroutineScope(Dispatchers.IO + webhookJob)

    // HttpClient is thread-safe and should be reused (avoids repeated SSL handshakes).
    // FIX #3: Now closed in shutdown() to release native TLS/socket resources.
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * EnvÃ­a el embed de inicio de juego.
     */
    fun sendGameStart(mapa: String, modo: String, survivors: List<Player>, killer: Player) {
        val webhookUrl = getWebhookUrl() ?: return

        val survivorsText = if (survivors.isEmpty()) {
            plugin.config.getString("discord.messages.waiting-players", "Esperando jugadores...")!!
        } else {
            survivors.joinToString("\\n") { it.name }
        }

        val json = buildJsonPayload(
            title = plugin.config.getString("discord.messages.start.title", "ðŸŽ® Â¡JUEGO INICIADO!")!!,
            color = 65280,
            fields = listOf(
                jsonField(plugin.config.getString("discord.messages.start.map-field", "ðŸ—ºï¸ Mapa")!!, mapa, inline = true),
                jsonField(plugin.config.getString("discord.messages.start.mode-field", "ðŸ•¹ï¸ Modo")!!, modo, inline = true),
                jsonField(plugin.config.getString("discord.messages.start.killer-field", "ðŸ©¸ Killer")!!, "**${killer.name.escape()}**", inline = false),
                jsonField(plugin.config.getString("discord.messages.start.survivors-field", "ðŸ‘¥ Survivors")!! + " (${survivors.size})", "```\\n${survivorsText.escape()}\\n```", inline = false)
            ),
            footer = plugin.config.getString("discord.messages.start.footer", "Mistaken Tracking â€¢ LIRIC-MISTAKEN 2.0")!!
        )

        dispatch(webhookUrl, json)
    }

    /**
     * EnvÃ­a el embed de fin de juego.
     */
    fun sendGameEnd(mapa: String, ganador: String, razon: String, survivorsNames: List<String>) {
        val webhookUrl = getWebhookUrl() ?: return

        val survivorsText = if (survivorsNames.isEmpty()) {
            plugin.config.getString("discord.messages.nobody-escaped", "Nadie escapÃ³...")!!
        } else {
            survivorsNames.joinToString("\\n")
        }

        val json = buildJsonPayload(
            title = plugin.config.getString("discord.messages.end.title", "ðŸ Â¡PARTIDA TERMINADA!")!!,
            description = "**${plugin.config.getString("discord.messages.end.result-text", "Resultado:")}** ${razon.escape()}",
            color = 16711680,
            fields = listOf(
                jsonField(plugin.config.getString("discord.messages.end.map-field", "ðŸ—ºï¸ Mapa")!!, mapa, inline = true),
                jsonField(plugin.config.getString("discord.messages.end.winner-field", "ðŸ† Ganador")!!, "**${ganador.escape()}**", inline = true),
                jsonField(plugin.config.getString("discord.messages.end.survived-field", "ðŸšª Sobrevivieron")!!, "```\\n${survivorsText.escape()}\\n```", inline = false)
            ),
            footer = plugin.config.getString("discord.messages.end.footer", "Mistaken Tracking â€¢ SesiÃ³n finalizada")!!
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
     * JSON control characters (U+0000â€“U+001F) that would produce invalid JSON.
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
