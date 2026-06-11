package liric.mistaken.data.db

import com.zaxxer.hikari.HikariConfig
import liric.mistaken.Mistaken
import java.sql.PreparedStatement

class PostgreSQLDatabaseManager(plugin: Mistaken) : AbstractSQLDatabaseManager(plugin) {

    override fun getHikariConfig(): HikariConfig {
        val config = plugin.config
        val hikariConfig = HikariConfig()

        val host = config.getString("database.postgresql.host", "localhost")
        val port = config.getInt("database.postgresql.port", 5432)
        val database = config.getString("database.postgresql.database", "minecraft")
        val user = config.getString("database.postgresql.username", "postgres")
        val pass = config.getString("database.postgresql.password", "")

        hikariConfig.jdbcUrl = "jdbc:postgresql://$host:$port/$database"
        hikariConfig.username = user
        hikariConfig.password = pass

        hikariConfig.maximumPoolSize = config.getInt("database.pool.maximum-pool-size", 10)
        hikariConfig.minimumIdle = config.getInt("database.pool.minimum-idle", 2)
        hikariConfig.maxLifetime = config.getLong("database.pool.max-lifetime", 1800000)
        hikariConfig.connectionTimeout = config.getLong("database.pool.connection-timeout", 30000)

        return hikariConfig
    }

    override val insertIgnoreStatsQuery = "INSERT INTO stats (uuid, username) VALUES (?, ?) ON CONFLICT (uuid) DO NOTHING"

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
        // En PostgreSQL usando EXCLUDED.columna no es necesario enviar los parámetros duplicados del UPDATE
        ps.setString(1, uuid); ps.setString(2, lang); ps.setString(3, killersOwned)
        ps.setString(4, killerSelected); ps.setString(5, survOwned); ps.setString(6, survSelected)
        ps.setString(7, nick); ps.setString(8, skin)
    }
}
