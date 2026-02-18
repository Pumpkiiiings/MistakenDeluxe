package liric.mistaken.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liric.mistaken.Mistaken
import org.bukkit.configuration.file.FileConfiguration
import java.sql.Connection
import java.sql.SQLException

/**
 * [LIRIC-MISTAKEN 2.0]
 * DatabaseManager: Gestión de conexiones de alto rendimiento con HikariCP.
 * Optimizado para Clever Cloud y entornos de bajos recursos.
 */
class DatabaseManager(private val plugin: Mistaken, config: FileConfiguration) {

    private lateinit var dataSource: HikariDataSource

    init {
        setupPool(config)
        createTables()
    }

    /**
     * Configura el pool de conexiones con optimizaciones para MySQL.
     */
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

            // --- OPTIMIZACIONES SÉNIOR ---
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

            // --- GESTIÓN DE RECURSOS (Zero-Lag) ---
            // Con 2 usuarios, no necesitamos un pool gigante.
            maximumPoolSize = config.getInt("mysql.pool.maximum-pool-size", 10)
            minimumIdle = config.getInt("mysql.pool.minimum-idle", 2)
            maxLifetime = config.getLong("mysql.pool.max-lifetime", 1800000) // 30 min
            connectionTimeout = config.getLong("mysql.pool.connection-timeout", 30000) // 30 sec

            poolName = "MistakenPool"
        }

        try {
            dataSource = HikariDataSource(hikariConfig)
        } catch (e: Exception) {
            plugin.logger.severe("No se pudo conectar a la base de datos MySQL. Revisa tu configuración.")
            throw e
        }
    }

    /**
     * Crea las tablas necesarias si no existen.
     */
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

        try {
            connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(statsQuery)
                    stmt.execute(linksQuery)
                }
            }
            plugin.logger.info("§a[Database] Tablas verificadas correctamente.")
        } catch (e: SQLException) {
            plugin.logger.severe("Error al crear tablas en la base de datos: ${e.message}")
        }
    }

    /**
     * Obtiene una conexión del pool.
     * Utiliza el bloque 'use' de Kotlin para asegurar el cierre automático.
     */
    val connection: Connection
        get() {
            if (!::dataSource.isInitialized || dataSource.isClosed) {
                throw SQLException("El DataSource no está disponible.")
            }
            return dataSource.connection
        }

    /**
     * Cierra el pool de conexiones.
     */
    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
        }
    }
}
