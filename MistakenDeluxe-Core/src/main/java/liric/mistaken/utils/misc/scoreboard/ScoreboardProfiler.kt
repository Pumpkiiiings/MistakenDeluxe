package liric.mistaken.utils.scoreboard

import liric.mistaken.MistakenLib
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance, allocation-free profiler for scoreboard operations.
 * Bounded memory (no per-player objects that grow indefinitely).
 */
object ScoreboardProfiler {

    private val totalRenderTimeNs = AtomicLong(0)
    private val maxRenderTimeNs = AtomicLong(0)
    private val rendersCompleted = AtomicInteger(0)
    private val packetsSent = AtomicInteger(0)
    private val rendersSkipped = AtomicInteger(0)

    /**
     * Record the time taken for a single player render.
     */
    fun recordRender(timeNs: Long) {
        totalRenderTimeNs.addAndGet(timeNs)
        rendersCompleted.incrementAndGet()
        
        var currentMax = maxRenderTimeNs.get()
        while (timeNs > currentMax) {
            if (maxRenderTimeNs.compareAndSet(currentMax, timeNs)) {
                break
            }
            currentMax = maxRenderTimeNs.get()
        }
    }

    /**
     * Record that a render was completely skipped due to cache hits (dirty flags).
     */
    fun recordSkip() {
        rendersSkipped.incrementAndGet()
    }

    /**
     * Record a batch of packets sent.
     */
    fun recordPackets(count: Int) {
        packetsSent.addAndGet(count)
    }

    /**
     * Resets metrics. Designed to be called periodically (e.g. every 10 seconds or via command)
     * to prevent long-term overflow and provide current window metrics.
     */
    fun reset() {
        totalRenderTimeNs.set(0)
        maxRenderTimeNs.set(0)
        rendersCompleted.set(0)
        packetsSent.set(0)
        rendersSkipped.set(0)
    }

    fun getAverageRenderTimeMs(): Double {
        val count = rendersCompleted.get()
        if (count == 0) return 0.0
        return (totalRenderTimeNs.get().toDouble() / count) / 1_000_000.0
    }

    fun getMaxRenderTimeMs(): Double {
        return maxRenderTimeNs.get() / 1_000_000.0
    }

    fun getPacketsSent(): Int = packetsSent.get()
    
    fun getRendersSkipped(): Int = rendersSkipped.get()
    
    fun getRendersCompleted(): Int = rendersCompleted.get()

    fun printReport() {
        MistakenLib.log(MistakenLib.LogCategory.SCOREBOARD, "--- Scoreboard Profiler Report ---")
        MistakenLib.log(MistakenLib.LogCategory.SCOREBOARD, "Average Render Time: ${String.format("%.4f", getAverageRenderTimeMs())} ms")
        MistakenLib.log(MistakenLib.LogCategory.SCOREBOARD, "Max Render Time: ${String.format("%.4f", getMaxRenderTimeMs())} ms")
        MistakenLib.log(MistakenLib.LogCategory.SCOREBOARD, "Total Renders: ${getRendersCompleted()}")
        MistakenLib.log(MistakenLib.LogCategory.SCOREBOARD, "Renders Skipped (Clean): ${getRendersSkipped()}")
        MistakenLib.log(MistakenLib.LogCategory.SCOREBOARD, "Packets Sent: ${getPacketsSent()}")
        MistakenLib.log(MistakenLib.LogCategory.SCOREBOARD, "----------------------------------")
    }
}
