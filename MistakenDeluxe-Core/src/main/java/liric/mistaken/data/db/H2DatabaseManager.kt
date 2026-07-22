package liric.mistaken.data.db

import com.zaxxer.hikari.HikariConfig
import liric.mistaken.Mistaken
import java.io.File
import java.sql.PreparedStatement
import org.h2.Driver

class H2DatabaseManager(plugin: Mistaken) : AbstractSQLDatabaseManager(plugin) {

    override fun getHikariConfig(): HikariConfig {
        val hikariConfig = HikariConfig()

        val dbFile = File(plugin.dataFolder, "database")
        if (!dbFile.parentFile.exists()) {
            dbFile.parentFile.mkdirs()
        }

        // MODE=PostgreSQL to allow ON CONFLICT syntax
        hikariConfig.jdbcUrl = "jdbc:h2:file:${dbFile.absolutePath};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
        hikariConfig.driverClassName = "org.h2.Driver"

        hikariConfig.maximumPoolSize = 10
        hikariConfig.connectionTestQuery = "SELECT 1"

        return hikariConfig
    }

    override val insertIgnoreStatsQuery = "MERGE INTO stats (uuid, username) KEY(uuid) VALUES (?, ?)"

    override val upsertPlayerDataQuery = """
        MERGE INTO mistaken_player_data
        (uuid, lang, killers_owned, killer_selected, survivors_owned, survivor_selected, nick, skin_source)
        KEY (uuid)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    override fun bindUpsertVariables(ps: PreparedStatement, uuid: String, lang: String, killersOwned: String, killerSelected: String, survOwned: String, survSelected: String, nick: String, skin: String) {
        ps.setString(1, uuid); ps.setString(2, lang); ps.setString(3, killersOwned)
        ps.setString(4, killerSelected); ps.setString(5, survOwned); ps.setString(6, survSelected)
        ps.setString(7, nick); ps.setString(8, skin)
    }
}
