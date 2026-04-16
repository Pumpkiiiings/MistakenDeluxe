package liric.mistaken.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liric.mistaken.Mistaken
import org.bukkit.configuration.file.FileConfiguration
import java.sql.Connection
import java.sql.SQLException
import kotlin.use

/**
 *[LIRIC-MISTAKEN 2.0]
 * DatabaseManager: Gestión de conexiones HikariCP.
 * FIX: Adaptado a Network/Velocity con almacenamiento de perfiles completo.
 */
class DatabaseManager(private val plugin: Mistaken, config: FileConfiguration) {

    private lateinit var dataSource: HikariDataSource

    init {
        setupPool(config)
        createTables()
    }

    private fun setupPool(config: FileConfiguration) {
        val hikariConfig = HikariConfig().apply {
            val host = config.getString("mysql.host", "localhost")
            val port = config.getInt("mysql.port", 3306)
            val database = config.getString("mysql.database", "minecraft")
            val user = config.getString("mysql.username", "root")
            val pass = config.getString("mysql.password", "")

            jdbcUrl = "jdbc:mysql://$host:$port/$database"
            username = user
            password = pass

            // Optimizaciones de rendimiento para MySQL
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("useLocalSessionState", "true")
            addDataSourceProperty("rewriteBatchedStatements", "true")
            addDataSourceProperty("cacheResultSetMetadata", "true")
            addDataSourceProperty("cacheServerConfiguration", "true")
            addDataSourceProperty("elideSetAutoCommits", "true")
            addDataSourceProperty("maintainTimeStats", "false")

            maximumPoolSize = config.getInt("mysql.pool.maximum-pool-size", 10)
            minimumIdle = config.getInt("mysql.pool.minimum-idle", 2)
            maxLifetime = config.getLong("mysql.pool.max-lifetime", 1800000)
            connectionTimeout = config.getLong("mysql.pool.connection-timeout", 30000)

            poolName = "MistakenPool"
        }

        try {
            dataSource = HikariDataSource(hikariConfig)
        } catch (e: Exception) {
            plugin.componentLogger.error(plugin.mm.deserialize("<red>No se pudo conectar a la base de datos MySQL.</red>"))
            throw e
        }
    }

    private fun createTables() {
        val statsQuery = """
            CREATE TABLE IF NOT EXISTS stats (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                wins_survivor INT DEFAULT 0,
                wins_assassin INT DEFAULT 0,
                losses_survivor INT DEFAULT 0,
                losses_assassin INT DEFAULT 0,
                deaths INT DEFAULT 0,
                kills INT DEFAULT 0,
                asesino_equipado VARCHAR(32) DEFAULT 'slasher'
            );
        """.trimIndent()

        val linksQuery = """
            CREATE TABLE IF NOT EXISTS discord_links (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                discord_id VARCHAR(20) DEFAULT NULL,
                code VARCHAR(6) DEFAULT NULL
            );
        """.trimIndent()

        // 🔥 NUEVA TABLA: Almacena todo el perfil del jugador
        val playerDataQuery = """
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

        try {
            connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(statsQuery)
                    stmt.execute(linksQuery)
                    stmt.execute(playerDataQuery) // Creamos la nueva tabla
                }
            }
            plugin.componentLogger.info(plugin.mm.deserialize("<green>[Database] Tablas verificadas correctamente.</green>"))
        } catch (e: SQLException) {
            plugin.componentLogger.error(plugin.mm.deserialize("<red>Error al crear tablas en la base de datos: ${e.message}</red>"))
        }
    }

    // =========================================================================
    // 🔥 MÉTODOS PARA PLAYER DATA MANAGER
    // =========================================================================

    /**
     * Guarda o actualiza el perfil completo del jugador.
     */
    fun savePlayerDataRaw(uuid: String, lang: String, killersOwned: String, killerSelected: String, survOwned: String, survSelected: String, nick: String, skin: String) {
        val query = """
            INSERT INTO mistaken_player_data 
            (uuid, lang, killers_owned, killer_selected, survivors_owned, survivor_selected, nick, skin_source) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE 
            lang = ?, killers_owned = ?, killer_selected = ?, survivors_owned = ?, survivor_selected = ?, nick = ?, skin_source = ?
        """.trimIndent()

        try {
            connection.use { conn ->
                conn.prepareStatement(query).use { ps ->
                    // INSERT
                    ps.setString(1, uuid); ps.setString(2, lang); ps.setString(3, killersOwned)
                    ps.setString(4, killerSelected); ps.setString(5, survOwned); ps.setString(6, survSelected)
                    ps.setString(7, nick); ps.setString(8, skin)

                    // UPDATE
                    ps.setString(9, lang); ps.setString(10, killersOwned); ps.setString(11, killerSelected)
                    ps.setString(12, survOwned); ps.setString(13, survSelected); ps.setString(14, nick); ps.setString(15, skin)

                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.componentLogger.error(plugin.mm.deserialize("<red>Error guardando datos de $uuid: ${e.message}</red>"))
        }
    }

    /**
     * Carga el perfil del jugador. Devuelve null si es nuevo.
     */
    fun loadPlayerData(uuid: String): Map<String, String>? {
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
            plugin.componentLogger.error(plugin.mm.deserialize("<red>Error cargando datos de $uuid: ${e.message}</red>"))
        }
        return null
    }

    val connection: Connection
        get() {
            if (!::dataSource.isInitialized || dataSource.isClosed) throw SQLException("DataSource no disponible.")
            return dataSource.connection
        }

    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) dataSource.close()
    }
}