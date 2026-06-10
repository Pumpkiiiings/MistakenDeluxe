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
            val stats = plugin.databaseManager.loadStats(uuid.toString(), name)
            if (stats != null) {
                cache[uuid] = stats
            } else {
                cache[uuid] = PlayerStats() // Fallback a vacío
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
        plugin.databaseManager.saveStats(uuid.toString(), stats)
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