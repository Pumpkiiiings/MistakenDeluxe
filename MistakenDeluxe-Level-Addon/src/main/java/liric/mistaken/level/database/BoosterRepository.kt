package liric.mistaken.level.database

import liric.mistaken.data.db.DatabaseProvider
import java.sql.ResultSet

data class BoosterData(val uuid: String, val multiplier: Double, val expiry: Long, val totalDuration: Long)

class BoosterRepository(private val provider: DatabaseProvider) {

    fun init() {
        val query = """
            CREATE TABLE IF NOT EXISTS mistaken_boosters (
                uuid VARCHAR(36) PRIMARY KEY,
                multiplier DOUBLE NOT NULL,
                expiry BIGINT NOT NULL,
                total_duration BIGINT NOT NULL DEFAULT 3600000
            )
        """.trimIndent()
        AddonQueryExecutor.executeUpdate(provider, query)

        try {
            AddonQueryExecutor.executeUpdate(provider, "ALTER TABLE mistaken_boosters ADD COLUMN total_duration BIGINT NOT NULL DEFAULT 3600000")
        } catch (e: Exception) {
            // Column might already exist
        }
    }

    fun loadAll(): List<BoosterData> {
        val query = "SELECT * FROM mistaken_boosters"
        val list = mutableListOf<BoosterData>()
        AddonQueryExecutor.executeQuery(provider, query, { rs: ResultSet ->
            while (rs.next()) {
                var totalDur = 3600000L
                try {
                    totalDur = rs.getLong("total_duration")
                } catch (e: Exception) {}
                
                list.add(BoosterData(
                    uuid = rs.getString("uuid"),
                    multiplier = rs.getDouble("multiplier"),
                    expiry = rs.getLong("expiry"),
                    totalDuration = totalDur
                ))
            }
            null
        })
        return list
    }

    fun save(entity: BoosterData) {
        val existsQuery = "SELECT uuid FROM mistaken_boosters WHERE uuid = ?"
        val exists = AddonQueryExecutor.executeQuery(provider, existsQuery, { rs -> rs.next() }, entity.uuid) == true
        
        if (exists) {
            val updateQuery = "UPDATE mistaken_boosters SET multiplier = ?, expiry = ?, total_duration = ? WHERE uuid = ?"
            AddonQueryExecutor.executeUpdate(provider, updateQuery, entity.multiplier, entity.expiry, entity.totalDuration, entity.uuid)
        } else {
            val insertQuery = "INSERT INTO mistaken_boosters (uuid, multiplier, expiry, total_duration) VALUES (?, ?, ?, ?)"
            AddonQueryExecutor.executeUpdate(provider, insertQuery, entity.uuid, entity.multiplier, entity.expiry, entity.totalDuration)
        }
    }

    fun delete(uuid: String) {
        val query = "DELETE FROM mistaken_boosters WHERE uuid = ?"
        AddonQueryExecutor.executeUpdate(provider, query, uuid)
    }
}
