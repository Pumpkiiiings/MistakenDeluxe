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
 * Utiliza Coroutines para evitar micro-lag durante la transición de equipamiento.
 */
class SupervivienteManager(private val plugin: Mistaken) {

    // Cache de supervivientes en partida (Thread-Safe)
    private val activeSurvivors = ConcurrentHashMap<UUID, Superviviente>()

    // Catálogo de clases disponibles
    private val availableClasses = ConcurrentHashMap<String, Superviviente>()

    // Scope para tareas de equipamiento y delays
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // Registro de catálogo optimizado
        listOf(
            Civil(), Repartidor()
        ).forEach { registrarClase(it) }
    }

    /**
     * Registra una clase en el catálogo global.
     */
    fun registrarClase(superviviente: Superviviente) {
        availableClasses[superviviente.id.lowercase()] = superviviente
    }

    /**
     * Busca una clase por su identificador (ID).
     */
    fun getClasePorId(id: String?): Superviviente? {
        return id?.lowercase()?.let { availableClasses[it] }
    }

    /**
     * Asigna una clase a un jugador y entrega el equipo de forma asíncrona.
     * Optimizado: El delay de 5 ticks ocurre fuera del Main Thread.
     */
    fun registrarSuperviviente(player: Player, clase: Superviviente) {
        val uuid = player.uniqueId
        activeSurvivors[uuid] = clase

        // Usamos corrutinas para el delay de seguridad post-teleport
        managerScope.launch {
            // 5 ticks = 250ms (aprox)
            delay(250)

            // Volvemos al hilo principal para tocar inventarios (API de Bukkit)
            withContext(Dispatchers.Main) {
                if (player.isOnline && activeSurvivors.containsKey(uuid)) {
                    clase.equipar(player)
                    player.updateInventory()
                }
            }
        }
    }

    /**
     * Remueve a un jugador del estado activo y limpia sus tareas.
     */
    fun removerSuperviviente(player: Player) {
        removerSuperviviente(player.uniqueId)
    }

    fun removerSuperviviente(uuid: UUID) {
        activeSurvivors.remove(uuid)?.let { clase ->
            val player = Bukkit.getPlayer(uuid)
            clase.cleanup(player)
        }
    }

    /**
     * Verifica si el jugador es un superviviente activo.
     */
    fun esSupervivienteActivo(player: Player?): Boolean {
        return player?.let { activeSurvivors.containsKey(it.uniqueId) } ?: false
    }

    /**
     * Obtiene la instancia de la clase que el jugador está usando.
     */
    fun getClase(player: Player?): Superviviente? {
        return player?.let { activeSurvivors[it.uniqueId] }
    }

    /**
     * Retorna una vista inmutable del catálogo de clases.
     */
    fun getClasesDisponibles(): Map<String, Superviviente> = availableClasses

    /**
     * Limpieza total del sistema de supervivientes.
     * Ideal para el final de la partida o desactivación del plugin.
     */
    fun limpiarTodo() {
        if (activeSurvivors.isEmpty()) return

        activeSurvivors.forEach { (uuid, clase) ->
            val player = Bukkit.getPlayer(uuid)
            clase.cleanup(player)
        }

        activeSurvivors.clear()
        // Cancelamos cualquier tarea de equipamiento pendiente
        managerScope.coroutineContext.cancelChildren()

        plugin.logger.info("§b[Mistaken] Limpieza de supervivientes completada.")
    }

    /**
     * Cierre definitivo del Manager.
     */
    fun shutdown() {
        limpiarTodo()
        managerScope.cancel()
    }
}
