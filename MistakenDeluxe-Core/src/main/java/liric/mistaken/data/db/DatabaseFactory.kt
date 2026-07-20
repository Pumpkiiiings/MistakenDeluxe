package liric.mistaken.data.db

import liric.mistaken.Mistaken
import liric.mistaken.data.DatabaseManager

object DatabaseFactory {
    fun create(plugin: Mistaken): DatabaseManager {
        val dbConfig = plugin.configManager.get("database.yml").getRaw()
        val type = dbConfig.getString("database.type", "mysql")?.lowercase() ?: "mysql"
        return when (type) {
            "postgresql" -> PostgreSQLDatabaseManager(plugin)
            "h2" -> H2DatabaseManager(plugin)
            else -> MySQLDatabaseManager(plugin)
        }
    }
}
