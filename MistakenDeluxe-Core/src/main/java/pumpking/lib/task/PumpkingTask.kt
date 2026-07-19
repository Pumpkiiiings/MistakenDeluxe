package pumpking.lib.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Centralized task and thread manager for pumpking.lib.
 * Reduces thread proliferation by sharing scopes and executors.
 *
 * FIX #2: supervisorJob is now a single shared Job so that shutdown()
 * cancels BOTH ioScope and asyncScope, preventing thread and coroutine leaks.
 */
object PumpkingTask {

    // Single SupervisorJob controls both scopes — cancel once, cancel all.
    private val supervisorJob = SupervisorJob()

    // Shared IO Scope for Database, File writing, Config loading, etc.
    val ioScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    // Shared Async Scope for heavy CPU-bound operations.
    val asyncScope = CoroutineScope(Dispatchers.Default + supervisorJob)

    // Shared Cache Cleanup Executor (1 thread is enough for all caches and cooldowns)
    val cacheExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Pumpking-Cache-Cleaner")
    }

    /**
     * FIX #2: Now correctly cancels both coroutine scopes before shutting down
     * the executor, preventing thread leaks on plugin disable/reload.
     */
    fun shutdown() {
        supervisorJob.cancel("PumpkingTask shutdown")
        cacheExecutor.shutdown()
    }
}
