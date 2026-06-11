package pumpking.lib.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import pumpking.lib.core.PumpkingLib
import pumpking.lib.task.PumpkingTask

/**
 * Generic CacheManager with optional TTL expiration.
 */
open class CacheManager<K : Any, V : Any>(
    private val expirationMillis: Long = -1 // -1 means no expiration
) {
    protected val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private var cleanupTask: ScheduledFuture<*>? = null

    init {
        if (expirationMillis > 0) {
            cleanupTask = PumpkingTask.cacheExecutor.scheduleAtFixedRate({ cleanup() }, 1, 1, TimeUnit.MINUTES)
        }
    }

    data class CacheEntry<V>(val value: V, var lastAccess: Long)

    open fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }

    open fun get(key: K): V? {
        val entry = cache[key] ?: return null
        entry.lastAccess = System.currentTimeMillis()
        return entry.value
    }

    open fun remove(key: K): V? {
        return cache.remove(key)?.value
    }

    open fun contains(key: K): Boolean {
        return cache.containsKey(key)
    }

    fun clear() {
        cache.clear()
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<K>()
        for ((key, entry) in cache) {
            if (now - entry.lastAccess > expirationMillis) {
                toRemove.add(key)
            }
        }
        for (key in toRemove) {
            onExpire(key, cache.remove(key)?.value)
        }
    }

    protected open fun onExpire(key: K, value: V?) {
        // Override to handle expiration (e.g., save to DB)
    }

    open fun shutdown() {
        cleanupTask?.cancel(false)
        cache.clear()
    }
}
