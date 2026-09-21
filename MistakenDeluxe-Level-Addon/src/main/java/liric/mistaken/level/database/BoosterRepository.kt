package liric.mistaken.level.database

import liric.mistaken.data.db.DatabaseProvider
import java.sql.ResultSet

data class BoosterData(val uuid: String, val multiplier: Double, val expiry: Long)

class BoosterRepository(private val provider: DatabaseProvider) {

    fun init() {
        val query = """
            CREATE TABLE IF NOT EXISTS mistaken_boosters (
                uuid VARCHAR(36) PRIMARY KEY,
                multiplier DOUBLE NOT NULL,
                expiry BIGINT NOT NULL
            )
        """.trimIndent()
        AddonQueryExecutor.executeUpdate(provider, query)
    }

    fun loadAll(): List<BoosterData> {
        val query = "SELECT * FROM mistaken_boosters"
        val list = mutableListOf<BoosterData>()
        AddonQueryExecutor.executeQuery(provider, query, { rs: ResultSet ->
            while (rs.next()) {
                list.add(BoosterData(
                    uuid = rs.getString("uuid"),
                    multiplier = rs.getDouble("multiplier"),
                    expiry = rs.getLong("expiry")
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
            val updateQuery = "UPDATE mistaken_boosters SET multiplier = ?, expiry = ? WHERE uuid = ?"
            AddonQueryExecutor.executeUpdate(provider, updateQuery, entity.multiplier, entity.expiry, entity.uuid)
        } else {
            val insertQuery = "INSERT INTO mistaken_boosters (uuid, multiplier, expiry) VALUES (?, ?, ?)"
            AddonQueryExecutor.executeUpdate(provider, insertQuery, entity.uuid, entity.multiplier, entity.expiry)
        }
    }

    fun delete(uuid: String) {
        val query = "DELETE FROM mistaken_boosters WHERE uuid = ?"
        AddonQueryExecutor.executeUpdate(provider, query, uuid)
    }
}
