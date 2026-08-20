package liric.mistaken.roles.killers.tracking

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import java.util.concurrent.ConcurrentHashMap

class KillerTaskTracker {
    private val tasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    fun track(task: ScheduledTask?) {
        if (task != null) {
            tasks.add(task)
        }
    }

    fun dispose() {
        tasks.forEach { if (!it.isCancelled) it.cancel() }
        tasks.clear()
    }
}

class KillerEventTracker {
    private val listeners = ConcurrentHashMap.newKeySet<Listener>()

    fun track(listener: Listener) {
        listeners.add(listener)
    }

    fun dispose() {
        listeners.forEach { HandlerList.unregisterAll(it) }
        listeners.clear()
    }
}

class KillerResourceTracker {
    private val cleanupHooks = ConcurrentHashMap.newKeySet<Runnable>()

    fun track(cleanupAction: Runnable) {
        cleanupHooks.add(cleanupAction)
    }

    fun dispose() {
        cleanupHooks.forEach {
            try {
                it.run()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        cleanupHooks.clear()
    }
}
