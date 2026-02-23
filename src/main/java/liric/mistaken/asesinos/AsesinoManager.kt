package liric.mistaken.asesinos

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.clases.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoManager: El mero jefe de los malos.
 * Optimizado: Se quitó la redundancia de armadura y se afinó el tiempo de carga.
 */
class AsesinoManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val asesinosActivos = ConcurrentHashMap<UUID, Asesino>()
    private val clasesDisponibles = ConcurrentHashMap<String, Asesino>()

    val catalogo: Map<String, Asesino> get() = clasesDisponibles

    init {
        // Registro de los pesados
        listOf(
            Slasher(), Herobrine(), Entity303(), NullAsesino(),
            ColorAndElectricity(), CharlieInferno(), Romeo(), Mariachi(),
            Devesto(), KasaneTeto(), Bendy()
        ).forEach { registrarClase(it) }
    }

    private fun registrarClase(asesino: Asesino) {
        clasesDisponibles[asesino.id.lowercase()] = asesino
    }

    fun actualizarAsesino(player: Player, claseId: String) {
        if (claseId.equals("none", ignoreCase = true)) {
            removerAsesino(player)
            return
        }
        val clase = getClasePorId(claseId) ?: return

        // Usamos el dispatcher que ya arreglamos para que no truene el hilo
        plugin.pluginScope.launch(plugin.bukkitDispatcher) {
            clase.cleanup(player)
        }
        plugin.componentLogger.info(mm.deserialize("<gray>${player.name} sincronizado con ${clase.nombre}</gray>"))
    }

    /**
     * 🔥 EL FIX DE LA ARMADURA:
     * Limpiamos todo y dejamos que la clase haga su chamba solita.
     */
    fun registrarAsesino(player: Player, asesino: Asesino) {
        val uuid = player.uniqueId

        // 1. Limpieza total inmediata (Hilo Principal)
        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4) // Dejarlo en ceros
        asesinosActivos[uuid] = asesino

        // Feedback
        player.sendMessage(plugin.messageConfig.getMessage(player, "killer.transform",
            Placeholder.component("name", mm.deserialize(asesino.nombre))))
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)

        // 2. Equipamiento con un solo delay
        // Le damos 15 ticks para que el server se relaje después del teleport
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!player.isOnline || !asesinosActivos.containsKey(uuid)) return@Runnable

            // 🔥 LA CLAVE: La clase se encarga de TODO su equipo
            asesino.equipar(player)
            asesino.mostrarTrail(player)

            // Ajustes finales de inventario
            player.inventory.heldItemSlot = 8
            player.updateInventory()
        }, 15L)
    }

    fun equiparAsesino(player: Player, claseId: String) {
        val clase = getClasePorId(claseId) ?: getClasePorId("slasher")
        clase?.let { registrarAsesino(player, it) }
    }

    /**
     * Este vato ahora solo sirve como "limpia-sobras" por si algo quedó en el inventario.
     */
    private fun autoEquiparArmadura(player: Player) {
        val inv = player.inventory
        val contents = inv.contents

        for (i in contents.indices) {
            val item = contents[i] ?: continue
            val typeName = item.type.name

            when {
                typeName.endsWith("_HELMET") && inv.helmet == null -> { inv.helmet = item; inv.setItem(i, null) }
                typeName.endsWith("_CHESTPLATE") && inv.chestplate == null -> { inv.chestplate = item; inv.setItem(i, null) }
                typeName.endsWith("_LEGGINGS") && inv.leggings == null -> { inv.leggings = item; inv.setItem(i, null) }
                typeName.endsWith("_BOOTS") && inv.boots == null -> { inv.boots = item; inv.setItem(i, null) }
            }
        }
    }

    fun removerAsesino(player: Player) {
        val asesino = asesinosActivos.remove(player.uniqueId)

        plugin.pluginScope.launch(plugin.bukkitDispatcher) {
            asesino?.cleanup(player) ?: clasesDisponibles.values.forEach { it.cleanup(player) }

            // Reset total de los fierros del jugador
            player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            player.health = 20.0
            player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
            player.isGlowing = false
            player.isSwimming = false
        }
    }

    fun removerTodosLosAsesinos() {
        asesinosActivos.keys.toList().forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { removerAsesino(it) }
        }
        asesinosActivos.clear()
        scope.coroutineContext.cancelChildren()
    }

    // --- GETTERS ---
    fun getClasePorId(id: String?): Asesino? = id?.lowercase()?.let { clasesDisponibles[it] }
    fun getAsesinoDelJugador(player: Player?): Asesino? = player?.let { asesinosActivos[it.uniqueId] }
    fun getAsesinoActual(): Player? = asesinosActivos.keys.firstOrNull()?.let { Bukkit.getPlayer(it) }?.takeIf { it.isOnline }
    fun esElAsesino(player: Player?): Boolean = player?.let { asesinosActivos.containsKey(it.uniqueId) } ?: false
    fun getClasesDisponibles(): Map<String, Asesino> = catalogo

    fun shutdown() {
        removerTodosLosAsesinos()
        scope.cancel()
    }
}
