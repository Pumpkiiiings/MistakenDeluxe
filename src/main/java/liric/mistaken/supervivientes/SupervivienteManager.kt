package liric.mistaken.supervivientes

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
 * OPTIMIZADO: Paper Schedulers Nativos (Sin Corrutinas).
 */
class SupervivienteManager(private val plugin: Mistaken) {

    private val mm = plugin.mm

    // Cache de supervivientes activos (Thread-Safe)
    private val activeSurvivors = ConcurrentHashMap<UUID, Superviviente>()

    // Catálogo de clases registradas
    private val availableClasses = ConcurrentHashMap<String, Superviviente>()

    init {
        // Registro de Clases (Singletons)
        // Aquí agregas las demás clases cuando las tengas listas (Jesse, Petra, etc.)
        listOf(
            Civil(),
            Repartidor(),
            Minty(),
            RaincoatKid(),
            KasaneTeto(),
            Jesse(),
            Aldeano(),
            Notch(),
            Troll()
        ).forEach { registrarClase(it) }
    }

    private fun registrarClase(superviviente: Superviviente) {
        availableClasses[superviviente.id.lowercase()] = superviviente
    }

    fun getClasePorId(id: String?): Superviviente? {
        return id?.lowercase()?.let { availableClasses[it] }
    }

    /**
     * 🔥 REGISTRO OPTIMIZADO (Paper 1.21.4+):
     * Usamos 'player.scheduler'. Si el jugador se desconecta antes de los 5 ticks,
     * la tarea se cancela sola automáticamente.
     */
    fun registrarSuperviviente(player: Player, clase: Superviviente) {
        val uuid = player.uniqueId

        // 1. Asignación inmediata en RAM
        activeSurvivors[uuid] = clase

        // 2. Tarea diferida anclada a la entidad (Safe)
        // Se ejecuta 5 ticks (250ms) después para asegurar que el inventario esté listo
        player.scheduler.runDelayed(plugin, { task ->
            // Verificamos si sigue siendo la misma clase (por si spameó clicks)
            if (activeSurvivors[uuid] == clase) {
                clase.equipar(player)
                player.updateInventory()

                plugin.componentLogger.info(mm.deserialize(
                    "<gray>[Superviviente]</gray> <white>${player.name}</white> <green>equipado como ${clase.nombre}</green>"
                ))

                // Feedback al jugador
                player.sendMessage(plugin.messageConfig.getMessage(player, "game.class-selected",
                    Placeholder.component("class", mm.deserialize(clase.nombre))))
            }
        }, null, 5L)
    }

    /**
     * Remueve al superviviente.
     * Detecta si el jugador está online para usar su Scheduler, o limpia solo la RAM si está offline.
     */
    fun removerSuperviviente(player: Player) {
        removerLogica(player.uniqueId, player)
    }

    fun removerSuperviviente(uuid: UUID) {
        val player = Bukkit.getPlayer(uuid)
        removerLogica(uuid, player)
    }

    private fun removerLogica(uuid: UUID, player: Player?) {
        val clase = activeSurvivors.remove(uuid) ?: return

        if (player != null && player.isOnline) {
            // 🔥 FOLIA FIX: Modificar inventario/efectos DEBE hacerse en el hilo de la entidad
            player.scheduler.run(plugin, { _ ->
                // 1. Limpieza lógica de la clase
                clase.cleanup(player)

                // 2. Limpieza física
                player.inventory.clear()
                player.inventory.armorContents = arrayOfNulls(4)

                // Limpieza de pociones eficiente
                player.activePotionEffects.forEach { effect ->
                    player.removePotionEffect(effect.type)
                }

                player.isSwimming = false
                player.walkSpeed = 0.2f
            }, null)
        } else {
            // Si está offline, solo limpiamos la lógica interna de la clase (si aplica)
            clase.cleanup(null)
        }
    }

    /**
     * Limpieza total al terminar la partida.
     */
    fun limpiarTodo() {
        // 1. Limpiamos a los jugadores individuales
        val iterador = activeSurvivors.keys.iterator()
        while (iterador.hasNext()) {
            val uuid = iterador.next()
            // Llamamos a la lógica de remoción (Bukkit.getPlayer maneja si es null)
            removerSuperviviente(uuid)
            iterador.remove()
        }

        activeSurvivors.clear()

        plugin.componentLogger.info(mm.deserialize("<aqua>[Mistaken]</aqua> <gray>Limpieza de supervivientes completada.</gray>"))
    }

    // --- GETTERS ---
    fun esSupervivienteActivo(player: Player?): Boolean = player?.let { activeSurvivors.containsKey(it.uniqueId) } ?: false
    fun getClase(player: Player?): Superviviente? = player?.let { activeSurvivors[it.uniqueId] }
    fun getClasesDisponibles(): Map<String, Superviviente> = availableClasses

    fun shutdown() {
        limpiarTodo()
    }
}
