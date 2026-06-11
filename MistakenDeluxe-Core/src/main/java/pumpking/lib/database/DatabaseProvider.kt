package pumpking.lib.database

import java.sql.Connection

interface DatabaseProvider {
    fun getConnection(): Connection
    fun close()
}
