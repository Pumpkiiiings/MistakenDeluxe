package liric.mistaken.data.stats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import liric.mistaken.Mistaken
import java.util.UUID

/**
 * PlayerStatsManager - Mistaken v2.0-LIRIC
 *
 * Gestión de estadísticas persistentes.
 * Optimización:
 * - Uso de Corrutinas (Dispatchers.IO) para no bloquear el Main Thread.
 * - Validación de columnas para seguridad.
 * - Auto-cierre de recursos JDBC.
 */
class PlayerStatsManager(private val plugin: Mistaken) {

    // Lista blanca de columnas permitidas para evitar inyección SQL
    private val allowedColumns = setOf(
        "kills", "deaths",
        "wins_survivor", "wins_assassin",
        "losses_survivor", "losses_assassin"
    )

    /**
     * Incrementa una estadística numérica de forma asíncrona.
     */
    fun addStat(uuid: UUID, playerName: String, column: String) {
        // Validación de seguridad (Critico cuando concatenas nombres de columnas)
        if (column !in allowedColumns) {
            plugin.logger.warning("⚠️ Intento de modificar columna inválida o inyección SQL: $column")
            return
        }

        // Lanzamos la tarea al hilo de IO (Input/Output)
        CoroutineScope(Dispatchers.IO).launch {
            val query = """
                INSERT INTO stats (uuid, username, $column) VALUES (?, ?, 1) 
                ON DUPLICATE KEY UPDATE $column = $column + 1, username = ?;
            """.trimIndent()

            try {
                // .use cierra la conexión automáticamente al terminar el bloque
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(query).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.setString(2, playerName)
                        stmt.setString(3, playerName) // Para actualizar el nombre si cambió
                        stmt.executeUpdate()
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("Error al incrementar estadística [$column]: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Actualiza el asesino equipado.
     */
    fun updateSelectedKiller(uuid: UUID, playerName: String, killerName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val query = """
                INSERT INTO stats (uuid, username, asesino_equipado) VALUES (?, ?, ?) 
                ON DUPLICATE KEY UPDATE asesino_equipado = ?, username = ?;
            """.trimIndent()

            try {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(query).use { stmt ->
                        // Valores INSERT
                        stmt.setString(1, uuid.toString())
                        stmt.setString(2, playerName)
                        stmt.setString(3, killerName)

                        // Valores UPDATE
                        stmt.setString(4, killerName)
                        stmt.setString(5, playerName)

                        stmt.executeUpdate()
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("Error al actualizar asesino equipado en DB: ${e.message}")
            }
        }
    }
}