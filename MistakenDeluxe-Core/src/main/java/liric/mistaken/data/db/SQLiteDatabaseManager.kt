package liric.mistaken.data.db

import com.zaxxer.hikari.HikariConfig
import liric.mistaken.Mistaken
import java.io.File
import java.sql.PreparedStatement

class SQLiteDatabaseManager(plugin: Mistaken) : AbstractSQLDatabaseManager(plugin) {

    override fun getHikariConfig(): HikariConfig {
        val hikariConfig = HikariConfig()
        
        val dbFile = File(plugin.dataFolder, "database.db")
        if (!dbFile.exists()) {
            dbFile.parentFile.mkdirs()
            dbFile.createNewFile()
        }

        hikariConfig.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        hikariConfig.driverClassName = "org.sqlite.JDBC"

        // SQLite no soporta conexiones concurrentes de escritura de forma nativa sin WAL
        hikariConfig.maximumPoolSize = 1
        hikariConfig.connectionTestQuery = "SELECT 1"

        return hikariConfig
    }

    override val insertIgnoreStatsQuery = "INSERT OR IGNORE INTO stats (uuid, username) VALUES (?, ?)"

    override val upsertPlayerDataQuery = """
        INSERT INTO mistaken_player_data 
        (uuid, lang, killers_owned, killer_selected, survivors_owned, survivor_selected, nick, skin_source) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?) 
        ON CONFLICT (uuid) DO UPDATE SET 
        lang = EXCLUDED.lang, 
        killers_owned = EXCLUDED.killers_owned, 
        killer_selected = EXCLUDED.killer_selected, 
        survivors_owned = EXCLUDED.survivors_owned, 
        survivor_selected = EXCLUDED.survivor_selected, 
        nick = EXCLUDED.nick, 
        skin_source = EXCLUDED.skin_source
    """.trimIndent()

    override fun bindUpsertVariables(ps: PreparedStatement, uuid: String, lang: String, killersOwned: String, killerSelected: String, survOwned: String, survSelected: String, nick: String, skin: String) {
        ps.setString(1, uuid); ps.setString(2, lang); ps.setString(3, killersOwned)
        ps.setString(4, killerSelected); ps.setString(5, survOwned); ps.setString(6, survSelected)
        ps.setString(7, nick); ps.setString(8, skin)
    }
}
