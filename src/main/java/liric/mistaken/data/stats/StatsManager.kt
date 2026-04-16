package liric.mistaken.data.stats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import liric.mistaken.Mistaken
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * StatsManager: Gestión de estadísticas con caché reactivo.
 * Optimizado para minimizar las llamadas a la base de datos y eliminar el lag de red.
 */
class StatsManager(private val plugin: Mistaken) {

    private val cache = ConcurrentHashMap<UUID, PlayerStats>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var autoSaveJob: Job? = null

    init {
        startAutoSave()
    }

    /**
     * Carga inicial del jugador.
     * Se ejecuta de forma asíncrona al entrar al servidor.
     */
    fun loadStats(uuid: UUID, name: String) {
        scope.launch {
            try {
                plugin.databaseManager.connection.use { conn ->
                    // 1. Asegurar que el jugador existe (INSERT IGNORE)
                    val insertQuery = "INSERT IGNORE INTO stats (uuid, username) VALUES (?, ?)"
                    conn.prepareStatement(insertQuery).use { ps ->
                        ps.setString(1, uuid.toString())
                        ps.setString(2, name)
                        ps.executeUpdate()
                    }

                    // 2. Cargar los datos actuales a la RAM
                    val selectQuery = "SELECT * FROM stats WHERE uuid = ?"
                    conn.prepareStatement(selectQuery).use { ps ->
                        ps.setString(1, uuid.toString())
                        val rs = ps.executeQuery()

                        val stats = PlayerStats()
                        if (rs.next()) {
                            stats.load(
                                rs.getInt("wins_survivor"),
                                rs.getInt("wins_assassin"),
                                rs.getInt("losses_survivor"),
                                rs.getInt("losses_assassin"),
                                rs.getInt("kills"),
                                rs.getInt("deaths")
                            )
                        }
                        cache[uuid] = stats
                    }
                }
            } catch (e: SQLException) {
                plugin.logger.severe("Error cargando estadísticas de $name: ${e.message}")
            }
        }
    }

    /**
     * Actualiza la RAM al instante (0ms latencia).
     * No toca la base de datos, evitando micro-tirones durante el juego.
     */
    fun incrementStat(uuid: UUID, column: String) {
        cache[uuid]?.incrementStat(column)
    }

    /**
     * Guarda y elimina al jugador de la RAM (QuitEvent).
     */
    fun unloadPlayer(uuid: UUID) {
        scope.launch {
            saveToDatabase(uuid)
            cache.remove(uuid)
        }
    }

    /**
     * Guarda un jugador específico en la DB.
     * Consolida todos los cambios de una sola vez.
     */
    private suspend fun saveToDatabase(uuid: UUID) {
        val stats = cache[uuid] ?: return

        val query = """
            UPDATE stats SET 
            wins_survivor=?, wins_assassin=?, losses_survivor=?, 
            losses_assassin=?, deaths=?, kills=? 
            WHERE uuid=?
        """.trimIndent()

        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(query).use { ps ->
                    ps.setInt(1, stats.winsSurvivor.get())
                    ps.setInt(2, stats.winsAssassin.get())
                    ps.setInt(3, stats.lossesSurvivor.get())
                    ps.setInt(4, stats.lossesAssassin.get())
                    ps.setInt(5, stats.deaths.get())
                    ps.setInt(6, stats.kills.get())
                    ps.setString(7, uuid.toString())
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            plugin.logger.warning("No se pudieron guardar stats de $uuid en MySQL.")
        }
    }

    /**
     * Ciclo de autoguardado asíncrono.
     * Se suspende sin bloquear hilos.
     */
    private fun startAutoSave() {
        autoSaveJob = scope.launch {
            while (isActive) {
                delay(300_000L) // 5 minutos exactos
                if (cache.isNotEmpty()) {
                    plugin.logger.info("Sincronizando estadísticas de ${cache.size} jugadores con MySQL...")
                    saveAllToDatabase()
                }
            }
        }
    }

    /**
     * Guarda a todos los jugadores en caché.
     */
    fun saveAllToDatabase() {
        // En Kotlin, el scope.launch dentro de un loop es sumamente ligero
        cache.keys.forEach { uuid ->
            scope.launch { saveToDatabase(uuid) }
        }
    }

    /**
     * Obtiene una estadística específica desde la RAM.
     */
    fun getStat(uuid: UUID, statName: String): Int {
        return cache[uuid]?.getStatValue(statName) ?: 0
    }

    /**
     * Obtiene el objeto completo de estadísticas.
     */
    fun getStats(uuid: UUID): PlayerStats {
        return cache[uuid] ?: PlayerStats()
    }

    /**
     * Cierre del manager.
     */
    fun shutdown() {
        autoSaveJob?.cancel()
        // Intentar un último guardado síncrono antes de apagar
        runBlocking {
            cache.keys.forEach { saveToDatabase(it) }
        }
        scope.cancel()
    }
}