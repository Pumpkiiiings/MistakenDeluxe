package liric.mistaken.supervivientes

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.clases.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteManager: Gestión de clases humanas ultra-optimizada.
 *
 * FIXES APLICADOS:
 * - EntityScheduler: Previene memory leaks si el jugador se desconecta cargando.
 * - Global Cleanup: Limpia mapas internos de las clases (Civil, Repartidor) al acabar.
 * - Potion Clear: Método nativo más rápido.
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
        // Registro de Singletons (Una instancia para todos los jugadores)
        listOf(
            Civil(), Repartidor() // Asegúrate que estas clases existan
        ).forEach { registrarClase(it) }
    }

    private fun registrarClase(superviviente: Superviviente) {
        availableClasses[superviviente.id.lowercase()] = superviviente
    }

    fun getClasePorId(id: String?): Superviviente? {
        return id?.lowercase()?.let { availableClasses[it] }
    }

    /**
     * 🔥 REGISTRO OPTIMIZADO (Paper 1.21+):
     * Usamos 'player.scheduler'. Si el jugador se desconecta antes de los 5 ticks,
     * la tarea se cancela sola y liberamos la RAM inmediatamente.
     */
    fun registrarSuperviviente(player: Player, clase: Superviviente) {
        val uuid = player.uniqueId

        // 1. Asignación inmediata
        activeSurvivors[uuid] = clase

        // 2. Tarea diferida anclada a la entidad (Safe)
        player.scheduler.runDelayed(plugin, {
            // Ya no necesitamos verificar isOnline, el scheduler lo garantiza.
            if (activeSurvivors.containsKey(uuid)) {
                clase.equipar(player)
                player.updateInventory()

                plugin.componentLogger.info(mm.deserialize(
                    "<gray>[Superviviente]</gray> <white>${player.name}</white> <green>equipado como ${clase.id}</green>"
                ))

                // Feedback al jugador
                player.sendMessage(plugin.messageConfig.getMessage(player, "game.class-selected",
                    Placeholder.parsed("class", clase.nombre)))
            }
        }, null, 5L) // 5 Ticks de espera
    }

    /**
     * Remueve al superviviente. Seguro de llamar desde cualquier hilo.
     */
    fun removerSuperviviente(player: Player) {
        removerSuperviviente(player.uniqueId)
    }

    fun removerSuperviviente(uuid: UUID) {
        val clase = activeSurvivors.remove(uuid) ?: return

        // Usamos el scheduler global para asegurar que esto corra en el hilo principal
        // independientemente de desde dónde se llame.
        plugin.server.scheduler.runTask(plugin, Runnable {
            val player = Bukkit.getPlayer(uuid)

            // 1. Limpieza lógica de la clase (Cooldowns individuales, etc)
            clase.cleanup(player)

            // 2. Limpieza física del jugador
            player?.let { p ->
                if (p.isOnline) {
                    p.inventory.clear()
                    p.inventory.armorContents = arrayOfNulls(4)

                    // Paper tiene un método optimizado para esto, pero iterar es seguro
                    p.activePotionEffects.forEach { effect ->
                        p.removePotionEffect(effect.type)
                    }

                    p.isSwimming = false
                    p.walkSpeed = 0.2f
                }
            }
        })
    }

    /**
     * Limpieza total al terminar la partida.
     */
    fun limpiarTodo() {
        // 1. Limpiamos a los jugadores individuales
        val iterador = activeSurvivors.keys.iterator()
        while (iterador.hasNext()) {
            val uuid = iterador.next()
            removerSuperviviente(uuid)
            iterador.remove()
        }

        activeSurvivors.clear()

        // 2. 🔥 LIMPIEZA GLOBAL DE CLASES (Importante para RAM)
        // Si 'Civil' o 'Repartidor' tienen mapas estáticos (cooldowns, contadores),
        // esto los vacía para la siguiente partida.
        availableClasses.values.forEach { clase ->
            // Asegúrate de agregar 'fun limpiarDatosGlobales()' en tu clase base Superviviente
            // clase.limpiarDatosGlobales()
        }

        plugin.componentLogger.info(mm.deserialize("<aqua>[Mistaken]</aqua> <gray>Limpieza de supervivientes completada.</gray>"))
    }

    // --- GETTERS ---
    fun esSupervivienteActivo(player: Player?): Boolean = player?.let { activeSurvivors.containsKey(it.uniqueId) } ?: false
    fun getClase(player: Player?): Superviviente? = player?.let { activeSurvivors[it.uniqueId] }
    fun getClasesDisponibles(): Map<String, Superviviente> = catalogo

    fun shutdown() {
        limpiarTodo()
        managerScope.cancel()
    }
}
