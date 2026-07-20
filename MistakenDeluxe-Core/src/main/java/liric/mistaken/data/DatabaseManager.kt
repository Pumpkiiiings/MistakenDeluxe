package liric.mistaken.data

import liric.mistaken.data.stats.PlayerStats
import java.sql.Connection

/**
 * [LIRIC-MISTAKEN 2.2]
 * DatabaseManager: Interfaz unificada para bases de datos (MySQL, PostgreSQL, SQLite).
 */
interface DatabaseManager {
    fun setup()
    fun close()

    val connection: Connection

    // Player Stats
    fun loadStats(uuid: String, username: String): PlayerStats?
    fun saveStats(uuid: String, stats: PlayerStats)

    // Player Profile Data
    fun loadPlayerData(uuid: String): Map<String, String>?
    fun savePlayerDataRaw(
        uuid: String,
        lang: String,
        killersOwned: String,
        killerSelected: String,
        survOwned: String,
        survSelected: String,
        nick: String,
        skin: String
    )
}
