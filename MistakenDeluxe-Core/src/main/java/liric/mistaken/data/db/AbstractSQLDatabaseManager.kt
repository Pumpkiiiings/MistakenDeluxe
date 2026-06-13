package liric.mistaken.data.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liric.mistaken.Mistaken
import liric.mistaken.data.DatabaseManager
import liric.mistaken.data.stats.PlayerStats
import java.sql.Connection
import java.sql.SQLException

abstract class AbstractSQLDatabaseManager(protected val plugin: Mistaken) : DatabaseManager {

    protected lateinit var dataSource: HikariDataSource

    override fun setup() {
        val config = getHikariConfig()
        config.poolName = "MistakenPool-${this::class.simpleName}"
        try {
            dataSource = HikariDataSource(config)
            createTables()
            plugin.componentLogger.info(plugin.mm.deserialize("[SUCCESS] [Database] Connection established (${this::class.simpleName})."))
        } catch (e: Exception) {
            plugin.componentLogger.error(plugin.mm.deserialize("[ERROR] [Database] Connection failed: ${e.message}"))
        }
    }

    override fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
        }
    }

    override val connection: Connection
        get() {
            if (!::dataSource.isInitialized || dataSource.isClosed) throw SQLException("DataSource is not available.")
            return dataSource.connection
        }

    protected abstract fun getHikariConfig(): HikariConfig

    // === QUERIES ESPECÃFICAS DE MOTOR (Sobrescribir en PostgreSQL/SQLite si difieren) ===
    protected open val createStatsTableQuery = """
        CREATE TABLE IF NOT EXISTS stats (
            uuid VARCHAR(36) PRIMARY KEY,
            username VARCHAR(16) NOT NULL,
            wins_survivor INT DEFAULT 0,
            wins_assassin INT DEFAULT 0,
            losses_survivor INT DEFAULT 0,
            losses_assassin INT DEFAULT 0,
            deaths INT DEFAULT 0,
            kills INT DEFAULT 0,
            generators_repaired INT DEFAULT 0,
            asesino_equipado VARCHAR(32) DEFAULT 'slasher'
        );
    """.trimIndent()

    protected open val createLinksTableQuery = """
        CREATE TABLE IF NOT EXISTS discord_links (
            uuid VARCHAR(36) PRIMARY KEY,
            username VARCHAR(16) NOT NULL,
            discord_id VARCHAR(20) DEFAULT NULL,
            code VARCHAR(6) DEFAULT NULL
        );
    """.trimIndent()

    protected open val createPlayerDataQuery = """
        CREATE TABLE IF NOT EXISTS mistaken_player_data (
            uuid VARCHAR(36) PRIMARY KEY,
            lang VARCHAR(10) DEFAULT 'es',
            killers_owned TEXT,
            killer_selected VARCHAR(32) DEFAULT 'slasher',
            survivors_owned TEXT,
            survivor_selected VARCHAR(32) DEFAULT 'civil',
            nick VARCHAR(32) DEFAULT '',
            skin_source VARCHAR(64) DEFAULT ''
        );
    """.trimIndent()

    protected open val insertIgnoreStatsQuery = "INSERT IGNORE INTO stats (uuid, username) VALUES (?, ?)"

    // Default: MySQL Syntax (ON DUPLICATE KEY UPDATE)
    protected open val upsertPlayerDataQuery = """
        INSERT INTO mistaken_player_data
        (uuid, lang, killers_owned, killer_selected, survivors_owned, survivor_selected, nick, skin_source)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
        lang = ?, killers_owned = ?, killer_selected = ?, survivors_owned = ?, survivor_selected = ?, nick = ?, skin_source = ?
    """.trimIndent()

    private fun createTables() {
        try {
            connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(createStatsTableQuery)
                    stmt.execute(createLinksTableQuery)
                    stmt.execute(createPlayerDataQuery)
                }
            }
        } catch (e: SQLException) {
            plugin.componentLogger.error(plugin.mm.deserialize("[ERROR] [Database] Failed to create tables: ${e.message}"))
        }
    }

    override fun loadStats(uuid: String, username: String): PlayerStats? {
        try {
            connection.use { conn ->
                // 1. Ensure exists
                conn.prepareStatement(insertIgnoreStatsQuery).use { ps ->
                    ps.setString(1, uuid)
                    ps.setString(2, username)
                    ps.executeUpdate()
                }

                // 2. Load data
                val query = "SELECT * FROM stats WHERE uuid = ?"
                conn.prepareStatement(query).use { ps ->
                    ps.setString(1, uuid)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        val stats = PlayerStats()
                        stats.load(
                            rs.getInt("wins_survivor"),
                            rs.getInt("wins_assassin"),
                            rs.getInt("losses_survivor"),
                            rs.getInt("losses_assassin"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("generators_repaired")
                        )
                        return stats
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Error loading stats for $username: ${e.message}")
        }
        return null
    }

    override fun saveStats(uuid: String, stats: PlayerStats) {
        val query = """
            UPDATE stats SET
                wins_survivor = ?, wins_assassin = ?,
                losses_survivor = ?, losses_assassin = ?,
                kills = ?, deaths = ?, generators_repaired = ?
            WHERE uuid = ?
        """.trimIndent()

        try {
            connection.use { conn ->
                conn.prepareStatement(query).use { ps ->
                    ps.setInt(1, stats.winsSurvivor.get())
                    ps.setInt(2, stats.winsAssassin.get())
                    ps.setInt(3, stats.lossesSurvivor.get())
                    ps.setInt(4, stats.lossesAssassin.get())
                    ps.setInt(5, stats.kills.get())
                    ps.setInt(6, stats.deaths.get())
                    ps.setInt(7, stats.generatorsRepaired.get())
                    ps.setString(8, uuid)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Error saving stats for $uuid: ${e.message}")
        }
    }

    override fun loadPlayerData(uuid: String): Map<String, String>? {
        val query = "SELECT * FROM mistaken_player_data WHERE uuid = ?"
        try {
            connection.use { conn ->
                conn.prepareStatement(query).use { ps ->
                    ps.setString(1, uuid)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        return mapOf(
                            "lang" to (rs.getString("lang") ?: "es"),
                            "killers_owned" to (rs.getString("killers_owned") ?: "slasher"),
                            "killer_selected" to (rs.getString("killer_selected") ?: "slasher"),
                            "survivors_owned" to (rs.getString("survivors_owned") ?: "civil"),
                            "survivor_selected" to (rs.getString("survivor_selected") ?: "civil"),
                            "nick" to (rs.getString("nick") ?: ""),
                            "skin_source" to (rs.getString("skin_source") ?: "")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Error loading player data for $uuid: ${e.message}")
        }
        return null
    }

    override fun savePlayerDataRaw(
        uuid: String, lang: String, killersOwned: String, killerSelected: String,
        survOwned: String, survSelected: String, nick: String, skin: String
    ) {
        try {
            connection.use { conn ->
                conn.prepareStatement(upsertPlayerDataQuery).use { ps ->
                    // Set variables for UPSERT logic which might differ per db engine
                    bindUpsertVariables(ps, uuid, lang, killersOwned, killerSelected, survOwned, survSelected, nick, skin)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Error saving player data for $uuid: ${e.message}")
        }
    }

    // Método virtual para bindear las variables del Upsert
    protected open fun bindUpsertVariables(ps: java.sql.PreparedStatement, uuid: String, lang: String, killersOwned: String, killerSelected: String, survOwned: String, survSelected: String, nick: String, skin: String) {
        // Formato por defecto para MySQL (15 params totales por duplicarse)
        ps.setString(1, uuid); ps.setString(2, lang); ps.setString(3, killersOwned)
        ps.setString(4, killerSelected); ps.setString(5, survOwned); ps.setString(6, survSelected)
        ps.setString(7, nick); ps.setString(8, skin)

        // UPDATE (MySQL)
        ps.setString(9, lang); ps.setString(10, killersOwned); ps.setString(11, killerSelected)
        ps.setString(12, survOwned); ps.setString(13, survSelected); ps.setString(14, nick); ps.setString(15, skin)
    }
}
