package liric.mistaken.data.db

import liric.mistaken.Mistaken
import liric.mistaken.data.DatabaseManager

object DatabaseFactory {
    fun create(plugin: Mistaken): DatabaseManager {
        val type = plugin.config.getString("database.type", "mysql")?.lowercase() ?: "mysql"
        return when (type) {
            "postgresql" -> PostgreSQLDatabaseManager(plugin)
            "sqlite" -> SQLiteDatabaseManager(plugin)
            else -> MySQLDatabaseManager(plugin)
        }
    }
}
