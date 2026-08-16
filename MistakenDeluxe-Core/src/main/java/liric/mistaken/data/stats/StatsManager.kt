package liric.mistaken.data.stats

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


class StatsManager(private val plugin: Mistaken) {

    private val cache = ConcurrentHashMap<UUID, PlayerStats>()
    private var autoSaveTask: ScheduledTask? = null

    init {
        startAutoSave()
    }

    /**
     * Carga inicial del jugador.
     * Se ejecuta de forma asíncrona al entrar al servidor.
     */
    fun loadStats(uuid: UUID, name: String) {
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
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
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            saveToDatabaseSync(uuid)
            cache.remove(uuid)
        }
    }

    /**
     * Guarda un jugador específico en la DB.
     * Consolida todos los cambios de una sola vez.
     */
    private fun saveToDatabaseSync(uuid: UUID) {
        val stats = cache[uuid] ?: return
        plugin.databaseManager.saveStats(uuid.toString(), stats)
    }

    /**
     * Ciclo de autoguardado asíncrono.
     * Se suspende sin bloquear hilos.
     */
    private fun startAutoSave() {
        autoSaveTask = plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (cache.isNotEmpty()) {
                plugin.logger.info("Sincronizando estadísticas de ${cache.size} jugadores con MySQL...")
                saveAllToDatabaseAsync()
            }
        }, 5L, 5L, TimeUnit.MINUTES)
    }

    /**
     * Guarda a todos los jugadores en caché.
     */
    fun saveAllToDatabaseAsync() {
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            saveConfigSync()
        }
    }

    /**
     * Guardado síncrono para el apagado del servidor.
     */
    fun saveConfigSync() {
        cache.keys.forEach { uuid ->
            saveToDatabaseSync(uuid)
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
        autoSaveTask?.cancel()
        saveConfigSync()
    }
}