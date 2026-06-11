package liric.mistaken.level.database

import pumpking.lib.database.DatabaseProvider
import pumpking.lib.database.Repository
import java.sql.ResultSet
import java.util.UUID

class LevelRepository(provider: DatabaseProvider) : Repository<UUID, PlayerLevelData>(provider) {


    override fun init() {
        val query = """
            CREATE TABLE IF NOT EXISTS mistaken_levels (
                uuid VARCHAR(36) PRIMARY KEY,
                level INT NOT NULL DEFAULT 1,
                experience BIGINT NOT NULL DEFAULT 0,
                total_experience BIGINT NOT NULL DEFAULT 0,
                prestige INT NOT NULL DEFAULT 0
            )
        """.trimIndent()
        AddonQueryExecutor.executeUpdate(provider, query)
    }

    override fun load(id: UUID): PlayerLevelData? {
        val query = "SELECT * FROM mistaken_levels WHERE uuid = ?"
        return AddonQueryExecutor.executeQuery(provider, query, { rs: ResultSet ->
            if (rs.next()) {
                PlayerLevelData(
                    uuid = UUID.fromString(rs.getString("uuid")),
                    level = rs.getInt("level"),
                    experience = rs.getLong("experience"),
                    totalExperience = rs.getLong("total_experience"),
                    prestige = rs.getInt("prestige")
                )
            } else {
                null
            }
        }, id.toString())
    }

    override fun save(entity: PlayerLevelData) {
        val query = """
            INSERT INTO mistaken_levels (uuid, level, experience, total_experience, prestige) 
            VALUES (?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE 
            level = VALUES(level), 
            experience = VALUES(experience), 
            total_experience = VALUES(total_experience), 
            prestige = VALUES(prestige)
        """.trimIndent()

        // Wait, SQLite doesn't support ON DUPLICATE KEY UPDATE exactly like MySQL.
        // It supports INSERT OR REPLACE, but we need compatibility with both.
        // Let's use standard approach or check if it's MySQL/SQLite.
        // For now, let's assume we can use a safe upsert, or do a select then insert/update.
        
        // Simpler compatible approach for generic use:
        val exists = load(entity.uuid) != null
        if (exists) {
            val updateQuery = "UPDATE mistaken_levels SET level = ?, experience = ?, total_experience = ?, prestige = ? WHERE uuid = ?"
            AddonQueryExecutor.executeUpdate(provider, updateQuery, entity.level, entity.experience, entity.totalExperience, entity.prestige, entity.uuid.toString())
        } else {
            val insertQuery = "INSERT INTO mistaken_levels (uuid, level, experience, total_experience, prestige) VALUES (?, ?, ?, ?, ?)"
            AddonQueryExecutor.executeUpdate(provider, insertQuery, entity.uuid.toString(), entity.level, entity.experience, entity.totalExperience, entity.prestige)
        }
    }

    override fun delete(id: UUID) {
        val query = "DELETE FROM mistaken_levels WHERE uuid = ?"
        AddonQueryExecutor.executeUpdate(provider, query, id.toString())
    }
}
