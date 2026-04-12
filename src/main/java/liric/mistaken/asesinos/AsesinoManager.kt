package liric.mistaken.asesinos

import liric.mistaken.Mistaken
import liric.mistaken.asesinos.clases.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 *[LIRIC-MISTAKEN 2.0]
 * AsesinoManager: El mero jefe de los malos.
 * FIX: Memory Leaks parchados (Soporte EntityScheduler nativo sin Corrutinas).
 */
class AsesinoManager(private val plugin: Mistaken) {

    private val mm = plugin.mm

    val asesinosActivos = ConcurrentHashMap<UUID, Asesino>()
    private val clasesDisponibles = ConcurrentHashMap<String, Asesino>()

    val catalogo: Map<String, Asesino> get() = clasesDisponibles

    init {
        listOf(
            Slasher(), Herobrine(), Entity303(), NullAsesino(),
            ColorAndElectricity(), CharlieInferno(), Romeo(), Mariachi(),
            Sowoul ()
        ).forEach { registrarClase(it) }
    }

    fun registrarClase(asesino: Asesino) {
        clasesDisponibles[asesino.id.lowercase()] = asesino
    }

    fun actualizarAsesino(player: Player, claseId: String) {
        if (claseId.equals("none", ignoreCase = true)) {
            removerAsesino(player)
            return
        }
        val clase = getClasePorId(claseId) ?: return

        // 🔥 FIX: Ejecutamos el cleanup de forma segura en el hilo del jugador (Entity Scheduler)
        player.scheduler.run(plugin, Consumer { _ ->
            clase.cleanup(player)
            plugin.componentLogger.info(mm.deserialize("<gray>${player.name} sincronizado con ${clase.nombre}</gray>"))
        }, null)
    }

    fun registrarAsesino(player: Player, asesino: Asesino) {
        val uuid = player.uniqueId

        // 1. Limpieza total inmediata (Hilo Principal)
        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4)
        asesinosActivos[uuid] = asesino

        // Feedback
        player.sendMessage(plugin.messageConfig.getMessage(player, "killer.transform",
            Placeholder.component("name", mm.deserialize(asesino.nombre))))
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)

        // 2. 🔥 FIX: EntityScheduler de Paper con runDelayed y Consumer explícito
        player.scheduler.runDelayed(
            plugin,
            Consumer { _ ->
                if (!player.isOnline || !asesinosActivos.containsKey(uuid)) return@Consumer

                asesino.equipar(player)
                asesino.mostrarTrail(player)
                player.inventory.heldItemSlot = 8
            },
            null,
            15L // 15 ticks de retraso
        )
    }

    fun equiparAsesino(player: Player, claseId: String) {
        val clase = getClasePorId(claseId) ?: getClasePorId("slasher")
        clase?.let { registrarAsesino(player, it) }
    }

    fun removerAsesino(player: Player) {
        val asesino = asesinosActivos.remove(player.uniqueId)

        // Limpiamos los datos del Asesino
        asesino?.cleanup(player) ?: clasesDisponibles.values.forEach { it.cleanup(player) }

        // Reset total de los fierros del jugador (Directo y rápido)
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.health = 20.0
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.isGlowing = false
        player.isSwimming = false
    }

    fun removerTodosLosAsesinos() {
        val iterador = asesinosActivos.keys.iterator()

        while (iterador.hasNext()) {
            val uuid = iterador.next()
            val player = plugin.server.getPlayer(uuid)

            if (player != null && player.isOnline) {
                removerAsesino(player)
            } else {
                asesinosActivos[uuid]?.let {
                    plugin.componentLogger.warn(mm.deserialize("<yellow>Limpiando datos fantasma del asesino desconectado: $uuid</yellow>"))
                }
            }
            iterador.remove()
        }

        asesinosActivos.clear()

        // Le decimos a todos los asesinos que vacíen su memoria RAM interna
        clasesDisponibles.values.forEach { asesino ->
            asesino.limpiarDatosGlobales()
        }
    }

    // --- GETTERS ---
    fun getClasePorId(id: String?): Asesino? = id?.lowercase()?.let { clasesDisponibles[it] }
    fun getAsesinoDelJugador(player: Player?): Asesino? = player?.let { asesinosActivos[it.uniqueId] }
    fun getAsesinoActual(): Player? = asesinosActivos.keys.firstOrNull()?.let { plugin.server.getPlayer(it) }?.takeIf { it.isOnline }
    fun esElAsesino(player: Player?): Boolean = player?.let { asesinosActivos.containsKey(it.uniqueId) } ?: false
    fun getClasesDisponibles(): Map<String, Asesino> = catalogo

    fun shutdown() {
        removerTodosLosAsesinos()
    }
}
