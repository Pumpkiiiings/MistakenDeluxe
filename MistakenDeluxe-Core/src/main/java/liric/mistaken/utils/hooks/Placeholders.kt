package liric.mistaken.utils.hooks

import liric.mistaken.Mistaken
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.GameMode

/**
 * [LIRIC-MISTAKEN 2.0]
 * MistakenExpansion: Integración con PlaceholderAPI adaptada a MULTIARENA.
 */
class Placeholders(private val plugin: Mistaken) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "mistaken"
    override fun getAuthor(): String = "Liric Development"
    override fun getVersion(): String = "2.0.0"
    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return ""

        val p = player.player // Jugador online (puede ser null)
        val param = params.lowercase()

        // 1. BUSCAR SESIÓN (Solo si el jugador está online)
        val session = p?.let { plugin.sessionManager.getSession(it) }

        return when (param) {
            // --- ESTADO DE LA PARTIDA ACTUAL (Contextual) ---
            "game_state" -> session?.currentState?.name ?: "LOBBY"
            "mode" -> session?.currentMode?.name ?: "N/A"
            "timer" -> session?.timer?.toString() ?: "0"
            "map" -> session?.currentMapName ?: "Lobby"
            "session_id" -> session?.id ?: "NONE"
            
            // --- OBSERVER PLACEHOLDERS ---
            "vivos" -> session?.getPlayers()?.count { !session.isKiller(it.uniqueId) && it.gameMode == GameMode.SURVIVAL && !it.isInvisible }?.toString() ?: "0"
            "id" -> session?.id ?: "NONE"
            "tiempo" -> session?.timer?.toString() ?: "0"
            "modo" -> session?.currentMode?.name ?: "N/A"
            "votes" -> {
                val arenas = plugin.arenaManager.getArenas()
                if (arenas.isEmpty()) "No hay mapas configurados"
                else {
                    val format = plugin.config.getString("visuals.vote-format", "<white>{map}</white> - <yellow>({votes})</yellow><newline>") ?: "<white>{map}</white> - <yellow>({votes})</yellow><newline>"
                    val builder = StringBuilder()
                    arenas.keys.forEach { mapName ->
                        val votes = session?.voteManager?.getVotesForMap(mapName) ?: 0
                        builder.append(format.replace("{map}", mapName).replace("{votes}", votes.toString()))
                    }
                    builder.toString().removeSuffix("<newline>")
                }
            }

            // --- GENERADORES (Contextuales a la arena del jugador) ---
            // Nota: Se asume que generatorManager puede filtrar por el mundo/sesión del jugador
            "gens_reparados" -> {
                if (p == null) "0"
                else plugin.generatorManager.getCompletedCountInWorld(p.world).toString()
            }
            "gens_total" -> {
                if (p == null) "0"
                else plugin.generatorManager.getTotalGeneratorsInWorld(p.world).toString()
            }

            // --- LÓGICA DE ROL ---
            "is_asesino" -> if (session?.isKiller(player.uniqueId) == true) "Si" else "No"

            "asesino_name" -> {
                if (p == null) return "N/A"
                plugin.asesinoManager.getKillerOfPlayer(p)?.nombre ?: "Ninguno"
            }

            "asesino_id" -> {
                if (p == null) return "none"
                plugin.asesinoManager.getKillerOfPlayer(p)?.id ?: "none"
            }

            // --- ESTADÍSTICAS GLOBALES (Independientes de la sesión) ---
            else -> {
                val stats = plugin.statsManager.getStats(player.uniqueId)
                when (param) {
                    "kills" -> stats.kills.get().toString()
                    "deaths" -> stats.deaths.get().toString()
                    "kdr" -> stats.formattedKDR
                    "wins_total" -> stats.totalWins.toString()
                    "wins_asesino" -> stats.winsAssassin.get().toString()
                    "wins_survivor" -> stats.winsSurvivor.get().toString()
                    "losses_total" -> stats.totalLosses.toString()
                    "games_played" -> stats.gamesPlayed.toString()
                    "stamina" -> plugin.playerDataManager.getStamina(player.uniqueId).toInt().toString()
                    else -> null
                }
            }
        }
    }
}
