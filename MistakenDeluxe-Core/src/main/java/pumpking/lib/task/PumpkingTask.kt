package pumpking.lib.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Centralized task and thread manager for pumpking.lib.
 * Reduces thread proliferation by sharing scopes and executors.
 */
object PumpkingTask {

    // Shared IO Scope for Database, File writing, Config loading, etc.
    val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Shared Async Scope for heavy CPU-bound operations.
    val asyncScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Shared Cache Cleanup Executor (1 thread is enough for all caches and cooldowns)
    val cacheExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Pumpking-Cache-Cleaner")
    }

    fun shutdown() {
        cacheExecutor.shutdown()
    }
}
