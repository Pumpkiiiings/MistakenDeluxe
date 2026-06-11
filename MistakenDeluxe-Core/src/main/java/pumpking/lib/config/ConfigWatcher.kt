package pumpking.lib.config

import kotlinx.coroutines.*
import org.bukkit.plugin.java.JavaPlugin
import pumpking.lib.core.PumpkingLib
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ConfigWatcher {
    private lateinit var watchService: WatchService
    private var job: Job? = null
    private val debounceCache = ConcurrentHashMap<String, Long>()

    fun init(plugin: JavaPlugin) {
        val configPath = plugin.dataFolder.toPath()
        if (!Files.exists(configPath)) Files.createDirectories(configPath)

        watchService = FileSystems.getDefault().newWatchService()
        configPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

        val menusPath = configPath.resolve("menus")
        if (Files.exists(menusPath)) {
            menusPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
        }

        job = pumpking.lib.task.PumpkingTask.ioScope.launch {
            while (isActive) {
                val key = try {
                    watchService.poll(1, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    null
                } catch (e: ClosedWatchServiceException) {
                    break
                }

                if (key == null) continue

                for (event in key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue

                    val file = event.context() as Path
                    val fileName = file.toString()

                    if (fileName.endsWith(".yml") || fileName.endsWith(".json")) {
                        val now = System.currentTimeMillis()
                        val lastUpdate = debounceCache.getOrDefault(fileName, 0L)

                        // 500ms debounce
                        if (now - lastUpdate > 500) {
                            debounceCache[fileName] = now
                            PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "Detected change in $fileName. Queuing reload...")
                            AutoApplyService.onFileChanged(fileName)
                        }
                    }
                }
                key.reset()
            }
        }
    }

    fun shutdown() {
        job?.cancel()
        if (::watchService.isInitialized) {
            try {
                watchService.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
