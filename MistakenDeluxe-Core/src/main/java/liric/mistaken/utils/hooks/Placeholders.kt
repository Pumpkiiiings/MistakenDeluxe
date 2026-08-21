package liric.mistaken.utils.hooks

import liric.mistaken.Mistaken
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.GameMode


class Placeholders(private val plugin: Mistaken) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "mistaken"
    override fun getAuthor(): String = "Liric Development"
    override fun getVersion(): String = "2.0.0"
    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return ""

        val p = player.player 
        val param = params.lowercase()

        fun formatTime(seconds: Int): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
            else String.format("%02d:%02d", m, s)
        }

        
        val session = p?.let { plugin.sessionManager.getSession(it) }

        return when (param) {
            
            "game_state" -> session?.currentState?.name ?: "LOBBY"
            "mode" -> session?.currentMode?.name ?: "N/A"
            "timer" -> session?.timer?.let { formatTime(it) } ?: "00:00"
            "map" -> session?.currentMapName ?: "Lobby"
            "session_id" -> session?.id ?: "NONE"
            
            
            "alive_survivors" -> session?.getPlayers()?.count { !session.isKiller(it.uniqueId) && it.gameMode == GameMode.SURVIVAL && !plugin.spectatorManager.isSpectator(it) }?.toString() ?: "0"
            "id" -> session?.id ?: "NONE"
            "time" -> session?.timer?.let { formatTime(it) } ?: "00:00"
            "votes" -> {
                val arenas = plugin.arenaManager.getArenas()
                if (arenas.isEmpty()) "No hay mapas configurados"
                else {
                    val format = plugin.config.getString("visuals.vote-format", "<gray>-</gray> <white>{map}</white> <yellow>({votes})</yellow><newline>") ?: "<gray>-</gray> <white>{map}</white> <yellow>({votes})</yellow><newline>"
                    val builder = StringBuilder()
                    arenas.map { it.name }.forEach { mapName ->
                        val votes = session?.voteManager?.getVotesForMap(mapName) ?: 0
                        builder.append(format.replace("{map}", mapName).replace("{votes}", votes.toString()))
                    }
                    builder.toString().removeSuffix("<newline>")
                }
            }

            
            
            "gens_repaired" -> {
                if (p == null) "0"
                else plugin.generatorManager.getCompletedCountInWorld(p.world).toString()
            }
            "total_gens" -> {
                if (p == null) "0"
                else plugin.generatorManager.getTotalGeneratorsInWorld(p.world).toString()
            }

            
            "is_killer" -> if (session?.isKiller(player.uniqueId) == true) "Yes" else "No"

            "killer_name" -> {
                if (p == null) return "N/A"
                plugin.killerManager.getKillerOfPlayer(p)?.nombre ?: "None"
            }

            "killer_id" -> {
                if (p == null) return "none"
                plugin.killerManager.getKillerOfPlayer(p)?.id ?: "none"
            }

            
            else -> {
                val stats = plugin.statsManager.getStats(player.uniqueId)
                when (param) {
                    "kills" -> stats.kills.get().toString()
                    "deaths" -> stats.deaths.get().toString()
                    "kdr" -> stats.formattedKDR
                    "wins_total" -> stats.totalWins.toString()
                    "wins_killer" -> stats.winsAssassin.get().toString()
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
