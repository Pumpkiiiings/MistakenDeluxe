package liric.mistaken.asesinos

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.clases.*
import liric.mistaken.utils.mainThread
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
 * AsesinoManager: Gestión de clases y estados de los asesinos.
 *
 * OPTIMIZACIÓN SUPREMA:
 * - Se reemplazó Coroutines por Bukkit Scheduler en tareas de inventario (elimina el lag de Spark).
 * - Detección de hilo inteligente para evitar saltos innecesarios.
 * - Limpieza de atributos moderna para 1.21.4.
 */
class AsesinoManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val asesinosActivos = ConcurrentHashMap<UUID, Asesino>()
    private val clasesDisponibles = ConcurrentHashMap<String, Asesino>()

    // Exponemos el catálogo como propiedad de solo lectura (más rápido que un método)
    val catalogo: Map<String, Asesino> get() = clasesDisponibles

    init {
        // Registro inicial de clases (Añadidos los nuevos asesinos)
        listOf(
            Slasher(), Herobrine(), Entity303(), NullAsesino(),
            ColorAndElectricity(), CharlieInferno()
        ).forEach { registrarClase(it) }
    }

    private fun registrarClase(asesino: Asesino) {
        clasesDisponibles[asesino.id.lowercase()] = asesino
    }

    /**
     * Sincroniza la clase sin efectos.
     */
    fun actualizarAsesino(player: Player, claseId: String) {
        if (claseId.equals("none", ignoreCase = true)) {
            removerAsesino(player)
            return
        }

        val clase = getClasePorId(claseId) ?: return

        // Ejecución inmediata si ya estamos en el hilo principal, si no, usamos el dispatcher
        val cleanupAction = { clase.cleanup(player) }
        if (Bukkit.isPrimaryThread()) cleanupAction() else Bukkit.getScheduler().runTask(plugin, cleanupAction)

        plugin.componentLogger.info(mm.deserialize("<gray>${player.name} sincronizado con ${clase.nombre}</gray>"))
    }

    /**
     * 🔥 FIX DE RENDIMIENTO: Registro de asesino.
     * Reemplazamos Coroutines por Bukkit Scheduler para evitar el overhead de 'resumeWith'.
     */
    fun registrarAsesino(player: Player, asesino: Asesino) {
        val uuid = player.uniqueId

        // 1. Reset instantáneo (Hilo Principal)
        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4)
        asesinosActivos[uuid] = asesino

        // Feedback visual inmediato
        player.sendMessage(plugin.messageConfig.getMessage(player, "killer.transform",
            Placeholder.component("name", mm.deserialize(asesino.nombre))))
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)

        // 2. Equipamiento escalonado (Uso de Bukkit Scheduler = Menos overhead que Coroutines para esto)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!player.isOnline || !asesinosActivos.containsKey(uuid)) return@Runnable

            // Equipamos el kit (Esto DEBE ser en el main thread)
            asesino.equipar(player)
            asesino.mostrarTrail(player)

            // 3. Auto-equipamiento de armadura (5 ticks después para asegurar carga de items)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (player.isOnline && asesinosActivos.containsKey(uuid)) {
                    autoEquiparArmadura(player)
                    player.inventory.heldItemSlot = 8 // Método explícito para evitar errores de API
                    player.updateInventory()
                }
            }, 5L)
        }, 10L)
    }

    /**
     * Método para equipar un asesino por su ID de clase.
     */
    fun equiparAsesino(player: Player, claseId: String) {
        val clase = getClasePorId(claseId) ?: getClasePorId("slasher")
        clase?.let { registrarAsesino(player, it) }
    }

    /**
     * Coloca automáticamente piezas de armadura.
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

        val task = {
            asesino?.cleanup(player) ?: clasesDisponibles.values.forEach { it.cleanup(player) }

            // Reset de Atributos Paper 1.21.4
            player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            player.health = 20.0
            player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
            player.isGlowing = false
            player.isSwimming = false
        }

        if (Bukkit.isPrimaryThread()) task() else Bukkit.getScheduler().runTask(plugin, task)
    }

    fun removerTodosLosAsesinos() {
        // Copia de seguridad para evitar ConcurrentModification
        val activos = asesinosActivos.keys.toList()
        activos.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { removerAsesino(it) }
        }
        asesinosActivos.clear()

        // Matar cualquier corrutina de cálculo de trail pendiente
        scope.coroutineContext.cancelChildren()
    }

    // --- GETTERS OPTIMIZADOS ---

    fun getClasePorId(id: String?): Asesino? = id?.lowercase()?.let { clasesDisponibles[it] }

    fun getAsesinoDelJugador(player: Player?): Asesino? = player?.let { asesinosActivos[it.uniqueId] }

    fun getAsesinoActual(): Player? =
        asesinosActivos.keys.firstOrNull()?.let { Bukkit.getPlayer(it) }?.takeIf { it.isOnline }

    fun esElAsesino(player: Player?): Boolean =
        player?.let { asesinosActivos.containsKey(it.uniqueId) } ?: false

    // Alias para el bucle externo que usaste
    fun getClasesDisponibles(): Map<String, Asesino> = catalogo

    fun shutdown() {
        removerTodosLosAsesinos()
        scope.cancel()
    }
}
