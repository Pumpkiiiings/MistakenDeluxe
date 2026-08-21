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
     * Carga inicial del player.
     * Se ejecuta de forma asÃ­ncrona al entrar al servidor.
     */
    fun loadStats(uuid: UUID, name: String) {
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            val stats = plugin.databaseManager.loadStats(uuid.toString(), name)
            if (stats != null) {
                cache[uuid] = stats
            } else {
                cache[uuid] = PlayerStats() // Fallback a vacÃ­o
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
     * Guarda y elimina al player de la RAM (QuitEvent).
     */
    fun unloadPlayer(uuid: UUID) {
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            saveToDatabaseSync(uuid)
            cache.remove(uuid)
        }
    }

    /**
     * Guarda un player especÃ­fico en la DB.
     * Consolida todos los cambios de una sola vez.
     */
    private fun saveToDatabaseSync(uuid: UUID) {
        val stats = cache[uuid] ?: return
        plugin.databaseManager.saveStats(uuid.toString(), stats)
    }

    /**
     * Ciclo de autoguardado asÃ­ncrono.
     * Se suspende sin bloquear hilos.
     */
    private fun startAutoSave() {
        autoSaveTask = plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (cache.isNotEmpty()) {
                plugin.componentLogger.info(pumpking.lib.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>Synchronizing statistics for ${cache.size} players with MySQL...</gray>"))
                saveAllToDatabaseAsync()
            }
        }, 5L, 5L, TimeUnit.MINUTES)
    }

    /**
     * Guarda a todos los players en cachÃ©.
     */
    fun saveAllToDatabaseAsync() {
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            saveConfigSync()
        }
    }

    /**
     * Guardado sÃ­ncrono para el apagado del servidor.
     */
    fun saveConfigSync() {
        cache.keys.forEach { uuid ->
            saveToDatabaseSync(uuid)
        }
    }

    /**
     * Obtiene una estadÃ­stica especÃ­fica desde la RAM.
     */
    fun getStat(uuid: UUID, statName: String): Int {
        return cache[uuid]?.getStatValue(statName) ?: 0
    }

    /**
     * Obtiene el objeto completo de estadÃ­sticas.
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