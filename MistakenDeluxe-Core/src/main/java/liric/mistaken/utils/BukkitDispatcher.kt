package liric.mistaken.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/**
 * [LIRIC-MISTAKEN 2.0]
 * BukkitDispatcher: Puente de alto rendimiento para Corrutinas.
 * Permite alternar entre hilos asíncronos y el hilo principal de Minecraft.
 */
class BukkitDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // Si el plugin se está apagando, no programamos más tareas para evitar fugas.
        if (!plugin.isEnabled) return

        // OPTIMIZACIÓN: Si ya estamos en el hilo principal, ejecutamos inmediatamente.
        // Esto evita el overhead del Scheduler de Bukkit.
        if (Bukkit.isPrimaryThread()) {
            block.run()
        } else {
            // Si estamos en un hilo asíncrono, saltamos al principal.
            Bukkit.getScheduler().runTask(plugin, block)
        }
    }
}

/**
 * Propiedad de extensión para acceder al Dispatcher de forma global.
 * Uso: withContext(plugin.mainThread) { ... }
 */
val Mistaken.mainThread: CoroutineDispatcher
    get() = BukkitDispatcher(this)
