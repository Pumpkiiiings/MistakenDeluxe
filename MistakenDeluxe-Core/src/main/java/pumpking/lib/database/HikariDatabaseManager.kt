package pumpking.lib.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import pumpking.lib.core.PumpkingLib
import java.io.File
import java.sql.Connection
import com.mysql.cj.jdbc.Driver

enum class DatabaseType {
    SQLITE, MYSQL, POSTGRESQL
}

class HikariDatabaseManager(
    private val type: DatabaseType,
    private val host: String = "localhost",
    private val port: Int = 3306,
    private val database: String = "minecraft",
    private val username: String = "root",
    private val password: String = "",
    private val dbFile: File? = null
) : DatabaseProvider {

    private lateinit var dataSource: HikariDataSource

    init {
        connect()
    }

    private fun connect() {
        val config = HikariConfig()

        when (type) {
            DatabaseType.SQLITE -> {
                if (dbFile == null) throw IllegalArgumentException("SQLite requires a dbFile")
                if (!dbFile.parentFile.exists()) dbFile.parentFile.mkdirs()

                config.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                config.driverClassName = "org.sqlite.JDBC"
                config.maximumPoolSize = 1 // SQLite does not handle concurrent writes well
            }
            DatabaseType.MYSQL -> {
                config.jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&autoReconnect=true"
                config.username = username
                config.password = password
                config.driverClassName = "Driver"
                config.maximumPoolSize = 10
            }
            DatabaseType.POSTGRESQL -> {
                config.jdbcUrl = "jdbc:postgresql://$host:$port/$database"
                config.username = username
                config.password = password
                config.driverClassName = "org.postgresql.Driver"
                config.maximumPoolSize = 10
            }
        }

        // Hikari optimizations
        if (type != DatabaseType.SQLITE) {
            config.addDataSourceProperty("cachePrepStmts", "true")
            config.addDataSourceProperty("prepStmtCacheSize", "250")
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            config.addDataSourceProperty("useServerPrepStmts", "true")
        }

        try {
            dataSource = HikariDataSource(config)
            PumpkingLib.log(PumpkingLib.LogCategory.CORE, "[Database] Connected to $type successfully.")
        } catch (e: Exception) {
            PumpkingLib.logError(PumpkingLib.LogCategory.CORE, "[Database] Failed to connect to $type: ${e.message}")
            throw e
        }
    }

    override fun getConnection(): Connection {
        return dataSource.connection
    }

    override fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            PumpkingLib.log(PumpkingLib.LogCategory.CORE, "[Database] Connection pool closed.")
        }
    }
}
