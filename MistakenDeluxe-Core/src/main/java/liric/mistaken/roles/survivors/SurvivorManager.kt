package liric.mistaken.roles.survivors

import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.clases.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * SurvivorManager: Gestión de clases humanas ultra-optimizada.
 * OPTIMIZADO: Paper Schedulers Nativos (Sin Corrutinas).
 */
class SurvivorManager(private val plugin: Mistaken) {

    private val mm = plugin.mm

    // Cache de supervivientes activos (Thread-Safe)
    private val activeSurvivors = ConcurrentHashMap<UUID, Survivor>()

    // Catálogo de clases registradas
    private val availableClasses = ConcurrentHashMap<String, Survivor>()

    init {
        // Registro de Clases (Singletons)
        // Aquí agregas las demás clases cuando las tengas listas (Jesse, Petra, etc.)
        listOf(
            Civilian(),
            DeliveryMan(),
            Minty(),
            RaincoatKid(),
            Jesse(),
            Villager(),
            Notch(),
            Troll()
        ).forEach { registerClass(it) }
    }

    private fun registerClass(superviviente: Survivor) {
        availableClasses[superviviente.id.lowercase()] = superviviente
    }

    fun getClassById(id: String?): Survivor? {
        return id?.lowercase()?.let { availableClasses[it] }
    }

    /**
     * 🔥 REGISTRO OPTIMIZADO (Paper 1.21.4+):
     * Usamos 'player.scheduler'. Si el jugador se desconecta antes de los 5 ticks,
     * la tarea se cancela sola automáticamente.
     */
    fun registrarSurvivor(player: Player, clase: Survivor) {
        val uuid = player.uniqueId

        // 1. Asignación inmediata en RAM
        activeSurvivors[uuid] = clase

        // 2. Tarea diferida anclada a la entidad (Safe)
        // Se ejecuta 5 ticks (250ms) después para asegurar que el inventario esté listo
        player.scheduler.runDelayed(plugin, { task ->
            // Verificamos si sigue siendo la misma clase (por si spameó clicks)
            if (activeSurvivors[uuid] == clase) {
                clase.equip(player)
                player.updateInventory()

                plugin.componentLogger.info(mm.deserialize(
                    "<gray>[Survivor]</gray> <white>${player.name}</white> <green>equipado como ${clase.nombre}</green>"
                ))

                // Feedback al jugador
                player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "game.class-selected",
                    Placeholder.component("class", mm.deserialize(clase.nombre))))
            }
        }, null, 5L)
    }

    /**
     * Remueve al superviviente.
     * Detecta si el jugador está online para usar su Scheduler, o limpia solo la RAM si está offline.
     */
    fun removerSurvivor(player: Player) {
        removeLogic(player.uniqueId, player)
    }

    fun removerSurvivor(uuid: UUID) {
        val player = Bukkit.getPlayer(uuid)
        removeLogic(uuid, player)
    }

    private fun removeLogic(uuid: UUID, player: Player?) {
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
    fun cleanAll() {
        // 1. Limpiamos a los jugadores individuales
        val iterador = activeSurvivors.keys.iterator()
        while (iterador.hasNext()) {
            val uuid = iterador.next()
            // Llamamos a la lógica de remoción (Bukkit.getPlayer maneja si es null)
            removerSurvivor(uuid)
            iterador.remove()
        }

        activeSurvivors.clear()

        plugin.componentLogger.info(mm.deserialize("[INFO] [Manager] Survivor cleanup completed."))
    }

    // --- GETTERS ---
    fun esSurvivorActivo(player: Player?): Boolean = player?.let { activeSurvivors.containsKey(it.uniqueId) } ?: false
    fun getSurvivorClass(player: Player?): Survivor? = player?.let { activeSurvivors[it.uniqueId] }
    fun getAvailableClasses(): Map<String, Survivor> = availableClasses

    fun shutdown() {
        cleanAll()
    }
}

