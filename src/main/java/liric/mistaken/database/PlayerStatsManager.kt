package liric.mistaken.database

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import java.sql.SQLException
import java.util.*

/**
 * [LIRIC-MISTAKEN 2.0]
 * PlayerStatsManager: Persistencia de estadísticas en tiempo real.
 * Usa Coroutines (Dispatchers.IO) para que las consultas SQL no afecten los TPS del servidor.
 */
class PlayerStatsManager(private val plugin: Mistaken) {

    // Scope dedicado a operaciones de base de datos para no saturar el servidor
    private val dbScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Incrementa una estadística (kills, deaths, wins, etc.) de forma asíncrona.
     * Optimizado con ON DUPLICATE KEY UPDATE para reducir el número de consultas.
     */
    fun addStat(uuid: UUID, playerName: String, column: String) {
        dbScope.launch {
            // Validamos la columna por seguridad (Prevenir SQL Injection aunque sea interna)
            val validColumns = listOf("wins_survivor", "wins_assassin", "losses_survivor", "losses_assassin", "deaths", "kills")
            if (column.lowercase() !in validColumns) return@launch

            val query = """
                INSERT INTO stats (uuid, username, $column) VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE $column = $column + 1, username = ?;
            """.trimIndent()

            try {
                plugin.dbManager.connection.use { conn ->
                    conn.prepareStatement(query).use { ps ->
                        ps.setString(1, uuid.toString())
                        ps.setString(2, playerName)
                        ps.setString(3, playerName)
                        ps.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                plugin.logger.severe("Error al incrementar estadística [$column] para $playerName: ${e.message}")
            }
        }
    }

    /**
     * Sincroniza el asesino seleccionado en la base de datos.
     * Esto permite que sistemas externos (como bots de Discord) lean el estado real.
     */
    fun updateSelectedKiller(uuid: UUID, playerName: String, killerName: String) {
        dbScope.launch {
            val query = """
                INSERT INTO stats (uuid, username, asesino_equipado) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE asesino_equipado = ?, username = ?;
            """.trimIndent()

            try {
                plugin.dbManager.connection.use { conn ->
                    conn.prepareStatement(query).use { ps ->
                        ps.setString(1, uuid.toString())
                        ps.setString(2, playerName)
                        ps.setString(3, killerName)
                        ps.setString(4, killerName)
                        ps.setString(5, playerName)
                        ps.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                plugin.logger.severe("Error al actualizar asesino equipado en DB para $playerName: ${e.message}")
            }
        }
    }

    /**
     * Cancela todas las peticiones de base de datos pendientes al cerrar el plugin.
     */
    fun shutdown() {
        dbScope.cancel()
    }
}
