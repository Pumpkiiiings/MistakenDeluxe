package liric.mistaken.data.db

import com.zaxxer.hikari.HikariConfig
import liric.mistaken.Mistaken

class MySQLDatabaseManager(plugin: Mistaken) : AbstractSQLDatabaseManager(plugin) {

    override fun getHikariConfig(): HikariConfig {
        val config = plugin.config
        val hikariConfig = HikariConfig()
        
        val host = config.getString("database.mysql.host", "localhost")
        val port = config.getInt("database.mysql.port", 3306)
        val database = config.getString("database.mysql.database", "minecraft")
        val user = config.getString("database.mysql.username", "root")
        val pass = config.getString("database.mysql.password", "")

        hikariConfig.jdbcUrl = "jdbc:mysql://$host:$port/$database"
        hikariConfig.username = user
        hikariConfig.password = pass

        // Optimizaciones de rendimiento para MySQL
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true")
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250")
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true")
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true")
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true")
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true")
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true")
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true")
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false")

        hikariConfig.maximumPoolSize = config.getInt("database.pool.maximum-pool-size", 10)
        hikariConfig.minimumIdle = config.getInt("database.pool.minimum-idle", 2)
        hikariConfig.maxLifetime = config.getLong("database.pool.max-lifetime", 1800000)
        hikariConfig.connectionTimeout = config.getLong("database.pool.connection-timeout", 30000)

        return hikariConfig
    }
}
