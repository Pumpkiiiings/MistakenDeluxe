package liric.mistaken.data.db

import java.sql.Connection

interface DatabaseProvider {
    fun getConnection(): Connection
    fun close()
}
