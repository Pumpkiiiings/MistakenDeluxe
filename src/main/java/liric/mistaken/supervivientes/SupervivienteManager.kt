package liric.mistaken.supervivientes

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.clases.*
import liric.mistaken.utils.mainThread // 1. IMPORTANTE: Usamos nuestra extensión para evitar el crash
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteManager: Gestión de clases humanas ultra-optimizada.
 *
 * Optimizaciones:
 * - Solucionado el error de Dispatchers.Main usando plugin.mainThread.
 * - Registro de clases asíncrono para evitar micro-tirones.
 * - Limpieza total de atributos y estados al remover jugadores.
 */
class SupervivienteManager(private val plugin: Mistaken) {

    // Cache de supervivientes activos (Thread-Safe para acceso desde AmbientManager)
    private val activeSurvivors = ConcurrentHashMap<UUID, Superviviente>()

    // Catálogo de clases registradas
    private val availableClasses = ConcurrentHashMap<String, Superviviente>()

    // Scope para tareas de equipamiento (Hilo de fondo por defecto)
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // Registro inicial del catálogo
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
     * Busca una clase por su ID (Case-insensitive).
     */
    fun getClasePorId(id: String?): Superviviente? {
        return id?.lowercase()?.let { availableClasses[it] }
    }

    /**
     * Asigna una clase a un jugador.
     * El delay de seguridad ocurre en segundo plano y el equipamiento en el hilo de Bukkit.
     */
    fun registrarSuperviviente(player: Player, clase: Superviviente) {
        val uuid = player.uniqueId
        activeSurvivors[uuid] = clase

        managerScope.launch {
            // --- HILO ASÍNCRONO ---
            // Esperamos 250ms (aprox 5 ticks) para asegurar que el jugador cargó el chunk tras el teleport
            delay(250)

            // --- VOLVER AL HILO PRINCIPAL (Bukkit) ---
            // Reemplazamos Dispatchers.Main por plugin.mainThread para evitar IllegalStateException
            withContext(plugin.mainThread) {
                if (player.isOnline && activeSurvivors.containsKey(uuid)) {
                    clase.equipar(player)
                    player.updateInventory()

                    plugin.componentLogger.info(plugin.mm.deserialize(
                        "<gray>[Superviviente]</gray> <white>${player.name}</white> <green>equipado como ${clase.id}</green>"
                    ))
                }
            }
        }
    }

    /**
     * Remueve a un jugador y limpia su estado físico/mental.
     */
    fun removerSuperviviente(player: Player) {
        removerSuperviviente(player.uniqueId)
    }

    fun removerSuperviviente(uuid: UUID) {
        val clase = activeSurvivors.remove(uuid) ?: return

        // Ejecutamos el cleanup en el hilo principal para evitar errores de API de Bukkit
        managerScope.launch(plugin.mainThread) {
            val player = Bukkit.getPlayer(uuid)
            clase.cleanup(player)

            // Si el jugador está online, reseteamos atributos básicos
            player?.let { p ->
                p.inventory.clear()
                p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            }
        }
    }

    /**
     * Verifica si el jugador es un superviviente activo.
     */
    fun esSupervivienteActivo(player: Player?): Boolean {
        return player?.let { activeSurvivors.containsKey(it.uniqueId) } ?: false
    }

    /**
     * Obtiene la instancia de la clase actual del jugador.
     */
    fun getClase(player: Player?): Superviviente? {
        return player?.let { activeSurvivors[it.uniqueId] }
    }

    /**
     * Catálogo completo de clases.
     */
    fun getClasesDisponibles(): Map<String, Superviviente> = availableClasses

    /**
     * Limpia a todos los supervivientes.
     */
    fun limpiarTodo() {
        if (activeSurvivors.isEmpty()) return

        // Copiamos las IDs para evitar errores de modificación durante la iteración
        val keys = activeSurvivors.keys().toList()

        keys.forEach { uuid ->
            removerSuperviviente(uuid)
        }

        activeSurvivors.clear()

        // Cancelar equipamientos que aún no se habían completado (delays)
        managerScope.coroutineContext.cancelChildren()

        plugin.componentLogger.info(plugin.mm.deserialize("<aqua>[Mistaken]</aqua> <gray>Limpieza de supervivientes completada.</gray>"))
    }

    /**
     * Cierre definitivo del Manager al apagar el plugin.
     */
    fun shutdown() {
        limpiarTodo()
        managerScope.cancel()
    }
}
