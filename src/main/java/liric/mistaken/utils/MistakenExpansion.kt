package liric.mistaken.utils

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import liric.mistaken.Mistaken
import liric.mistaken.data.PlayerStats
import org.bukkit.OfflinePlayer

/**
 * [LIRIC-MISTAKEN 2.0]
 * MistakenExpansion: Integración con PlaceholderAPI.
 * Optimización: Estructura de saltos 'when' y acceso a caché O(1).
 */
class MistakenExpansion(private val plugin: Mistaken) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "mistaken"

    override fun getAuthor(): String = "Liric Development"

    override fun getVersion(): String = "2.0.0"

    override fun persist(): Boolean = true

    /**
     * Procesa las peticiones de placeholders de forma ultra-rápida.
     */
    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return ""

        // Solo procesamos placeholders que requieren al jugador online si realmente lo está
        val p = player.player

        return when (params.lowercase()) {
            // --- ESTADO GLOBAL DEL JUEGO ---
            "game_state" -> plugin.gameManager.currentState.name
            "mode" -> plugin.gameManager.currentMode.name
            "timer" -> plugin.gameManager.timer.toString()
            "map" -> plugin.gameManager.currentMapName

            // --- GENERADORES ---
            "gens_reparados" -> plugin.generatorManager.getCompletedCount().toString()
            "gens_total" -> plugin.generatorManager.getTotalGenerators().toString()
            "gens_faltantes" -> (plugin.generatorManager.getTotalGenerators() - plugin.generatorManager.getCompletedCount()).toString()

            // --- LÓGICA DE JUGADOR (Online) ---
            "is_asesino" -> if (p != null && plugin.gameManager.esAsesino(p.uniqueId)) "Si" else "No"

            "asesino_nombre" -> {
                if (p == null) return "N/A"
                plugin.asesinoManager.getAsesinoDelJugador(p)?.nombre ?: "Ninguno"
            }

            "asesino_id" -> {
                if (p == null) return "none"
                plugin.asesinoManager.getAsesinoDelJugador(p)?.id ?: "none"
            }

            // --- ESTADÍSTICAS (Caché en RAM) ---
            else -> {
                val stats = plugin.statsManager.getStats(player.uniqueId)
                // Usamos AtomicInteger.get() del nuevo PlayerStats.kt
                when (params.lowercase()) {
                    "kills" -> stats.kills.get().toString()
                    "deaths" -> stats.deaths.get().toString()
                    "kdr" -> stats.formattedKDR
                    "wins_total" -> stats.totalWins.toString()
                    "wins_asesino" -> stats.winsAssassin.get().toString()
                    "wins_survivor" -> stats.winsSurvivor.get().toString()
                    "losses_total" -> stats.totalLosses.toString()
                    "games_played" -> stats.gamesPlayed.toString()
                    "stamina" -> plugin.playerDataManager.getStamina(player.uniqueId).toInt().toString()
                    else -> null // Placeholder no reconocido
                }
            }
        }
    }
}
