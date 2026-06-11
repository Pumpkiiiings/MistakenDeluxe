package liric.mistaken.level.database

import pumpking.lib.database.DatabaseProvider
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

object AddonQueryExecutor {

    fun executeUpdate(provider: DatabaseProvider, query: String, vararg parameters: Any): Int {
        var connection: Connection? = null
        var ps: PreparedStatement? = null
        return try {
            connection = provider.getConnection()
            ps = connection.prepareStatement(query)
            for (i in parameters.indices) {
                ps.setObject(i + 1, parameters[i])
            }
            ps.executeUpdate()
        } finally {
            ps?.close()
            connection?.close()
        }
    }

    fun <T> executeQuery(provider: DatabaseProvider, query: String, processor: (ResultSet) -> T, vararg parameters: Any): T? {
        var connection: Connection? = null
        var ps: PreparedStatement? = null
        var rs: ResultSet? = null
        return try {
            connection = provider.getConnection()
            ps = connection.prepareStatement(query)
            for (i in parameters.indices) {
                ps.setObject(i + 1, parameters[i])
            }
            rs = ps.executeQuery()
            processor(rs)
        } finally {
            rs?.close()
            ps?.close()
            connection?.close()
        }
    }
}
