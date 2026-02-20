package liric.mistaken.supervivientes

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.clases.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteManager: Gestión de clases humanas ultra-optimizada.
 *
 * OPTIMIZACIÓN SPARK:
 * - Se corrigió el error de Runnable en Bukkit Scheduler.
 * - Uso de colecciones concurrentes para evitar bloqueos.
 * - Limpieza de efectos de poción segura para la versión 1.21.4.
 */
class SupervivienteManager(private val plugin: Mistaken) {

    private val mm = plugin.mm

    // Cache de supervivientes activos (Thread-Safe)
    private val activeSurvivors = ConcurrentHashMap<UUID, Superviviente>()

    // Catálogo de clases registradas
    private val availableClasses = ConcurrentHashMap<String, Superviviente>()

    // Propiedad de solo lectura para acceso rápido
    val catalogo: Map<String, Superviviente> get() = availableClasses

    // Scope para tareas puramente asíncronas
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // Registro inicial del catálogo
        listOf(
            Civil(), Repartidor()
        ).forEach { registrarClase(it) }
    }

    fun registrarClase(superviviente: Superviviente) {
        availableClasses[superviviente.id.lowercase()] = superviviente
    }

    fun getClasePorId(id: String?): Superviviente? {
        return id?.lowercase()?.let { availableClasses[it] }
    }

    /**
     * 🔥 REGISTRO OPTIMIZADO:
     * Usamos Bukkit Scheduler para el delay de equipamiento.
     * Esto evita que Spark marque 'resumeWith' como un punto caliente de CPU.
     */
    fun registrarSuperviviente(player: Player, clase: Superviviente) {
        val uuid = player.uniqueId
        activeSurvivors[uuid] = clase

        // 5 ticks (250ms) es el estándar de oro para asegurar que el teleport finalizó.
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline && activeSurvivors.containsKey(uuid)) {
                clase.equipar(player)
                player.updateInventory()

                plugin.componentLogger.info(mm.deserialize(
                    "<gray>[Superviviente]</gray> <white>${player.name}</white> <green>equipado como ${clase.id}</green>"
                ))
            }
        }, 5L)
    }

    fun removerSuperviviente(player: Player) {
        removerSuperviviente(player.uniqueId)
    }

    fun removerSuperviviente(uuid: UUID) {
        val clase = activeSurvivors.remove(uuid) ?: return

        // --- SOLUCIÓN AL ERROR DE RUNNABLE ---
        // Al usar 'Runnable { ... }' Kotlin crea una instancia de la interfaz de Java,
        // permitiendo que Bukkit.getScheduler lo acepte sin errores.
        val cleanupTask = Runnable {
            val player = Bukkit.getPlayer(uuid)

            // 1. Limpieza de lógica de la clase
            clase.cleanup(player)

            // 2. Limpieza física del jugador
            player?.let { p ->
                p.inventory.clear()
                p.inventory.armorContents = arrayOfNulls(4)

                // En 1.21.4 es vital usar toList() para evitar ConcurrentModificationException
                p.activePotionEffects.toList().forEach { effect ->
                    p.removePotionEffect(effect.type)
                }

                // Reset de nado (Nuevo en Paper 1.21+)
                p.isSwimming = false
            }
        }

        // Ejecución inteligente: Si ya estamos en el hilo principal, ejecutamos de inmediato.
        if (Bukkit.isPrimaryThread()) {
            cleanupTask.run()
        } else {
            Bukkit.getScheduler().runTask(plugin, cleanupTask)
        }
    }

    /**
     * Verifica si el jugador es un superviviente activo.
     */
    fun esSupervivienteActivo(player: Player?): Boolean {
        return player?.let { activeSurvivors.containsKey(it.uniqueId) } ?: false
    }

    fun getClase(player: Player?): Superviviente? {
        return player?.let { activeSurvivors[it.uniqueId] }
    }

    fun getClasesDisponibles(): Map<String, Superviviente> = catalogo

    /**
     * Limpieza total al terminar la partida.
     */
    fun limpiarTodo() {
        if (activeSurvivors.isEmpty()) return

        // Copiamos llaves para evitar errores de modificación concurrente
        val keys = activeSurvivors.keys().toList()
        keys.forEach { uuid ->
            removerSuperviviente(uuid)
        }

        activeSurvivors.clear()
        plugin.componentLogger.info(mm.deserialize("<aqua>[Mistaken]</aqua> <gray>Limpieza de supervivientes completada.</gray>"))
    }

    fun shutdown() {
        limpiarTodo()
        managerScope.cancel()
    }
}
