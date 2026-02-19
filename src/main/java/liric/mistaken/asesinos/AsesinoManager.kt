package liric.mistaken.asesinos

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.clases.*
import liric.mistaken.utils.mainThread // 1. IMPORTANTE: Usamos nuestro puente para el hilo principal
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
 * Optimización:
 * - Solucionado el crash de Dispatchers.Main usando plugin.mainThread.
 * - Uso de Coroutines para distribuir la carga de equipamiento (No laguea al servidor).
 * - ConcurrentHashMap para asegurar thread-safety en entornos asíncronos.
 */
class AsesinoManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Mapas de datos protegidos para acceso multihilo
    val asesinosActivos = ConcurrentHashMap<UUID, Asesino>()
    private val clasesDisponibles = ConcurrentHashMap<String, Asesino>()

    init {
        // Registro inicial de clases
        listOf(
            Slasher(), Herobrine(), Entity303(), NullAsesino(), ColorAndElectricity()
        ).forEach { registrarClase(it) }
    }

    private fun registrarClase(asesino: Asesino) {
        clasesDisponibles[asesino.id.lowercase()] = asesino
    }

    // --- AGREGADO: Getter para compatibilidad con bucles externos ---
    fun getClasesDisponibles(): Map<String, Asesino> {
        return clasesDisponibles
    }

    /**
     * Sincroniza la clase sin efectos. Útil para el selector del lobby.
     */
    fun actualizarAsesino(player: Player, claseId: String) {
        if (claseId.equals("none", ignoreCase = true)) {
            removerAsesino(player)
            return
        }

        val clase = getClasePorId(claseId) ?: return
        // Cleanup preventivo en el hilo principal
        scope.launch(plugin.mainThread) {
            clase.cleanup(player)
        }

        plugin.componentLogger.info(mm.deserialize("<gray>${player.name} seleccionó a ${clase.nombre}</gray>"))
    }

    /**
     * ¡Transformación!
     * Distribuye la carga de equipamiento en varios ticks para evitar tirones de lag.
     */
    fun registrarAsesino(player: Player, asesino: Asesino) {
        val uuid = player.uniqueId

        // 1. Limpieza inmediata (Debe ser Síncrono)
        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4)
        asesinosActivos[uuid] = asesino

        // Feedback visual/auditivo
        player.sendMessage(plugin.messageConfig.getMessage(player, "killer.transform",
            Placeholder.component("name", mm.deserialize(asesino.nombre))))
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)

        // 2. Proceso de Equipamiento Asíncrono
        scope.launch {
            // Espera pequeña para que el servidor procese el estado inicial (10 ticks)
            delay(500L)

            if (!player.isOnline || !asesinosActivos.containsKey(uuid)) return@launch

            // --- VOLVER AL HILO PRINCIPAL (Bukkit) ---
            withContext(plugin.mainThread) {
                // Equipamos el kit de la clase
                asesino.equipar(player)

                // Efectos visuales iniciales
                asesino.mostrarTrail(player)
            }

            // 3. Auto-equipamiento de armadura (5 ticks después)
            delay(250L)

            if (player.isOnline) {
                withContext(plugin.mainThread) {
                    autoEquiparArmadura(player)
                    player.inventory.heldItemSlot = 8
                    player.updateInventory()

                    plugin.componentLogger.info(mm.deserialize("<red>[Asesino] ${player.name} equipado como ${asesino.id}</red>"))
                }
            }
        }
    }

    /**
     * AGREGADO: Metodo para equipar un asesino por su ID de clase.
     */
    fun equiparAsesino(player: Player, claseId: String) {
        val clase = getClasePorId(claseId) ?: getClasePorId("slasher")
        clase?.let { registrarAsesino(player, it) }
    }

    /**
     * Coloca automáticamente piezas de armadura que estén en el inventario.
     */
    private fun autoEquiparArmadura(player: Player) {
        val inv = player.inventory
        val contents = inv.contents

        for (i in contents.indices) {
            val item = contents[i] ?: continue
            if (item.type == Material.AIR) continue

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

        // Cleanup en hilo principal para evitar errores de API
        scope.launch(plugin.mainThread) {
            asesino?.cleanup(player) ?: clasesDisponibles.values.forEach { it.cleanup(player) }

            // Reset total de atributos (1.21.4)
            player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            player.health = 20.0
            player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
            player.isGlowing = false
            player.isSwimming = false
        }
    }

    fun removerTodosLosAsesinos() {
        // Copiamos la lista para evitar errores de modificación concurrente
        val activos = asesinosActivos.keys().toList()

        activos.forEach { uuid ->
            val p = Bukkit.getPlayer(uuid)
            if (p != null) removerAsesino(p)
        }

        asesinosActivos.clear()
        // Cancelamos equipamientos que estaban a mitad de camino
        scope.coroutineContext.cancelChildren()
    }

    // --- GETTERS ---

    fun getAsesinoDelJugador(player: Player?): Asesino? {
        return player?.let { asesinosActivos[it.uniqueId] }
    }

    fun getClasePorId(id: String?): Asesino? {
        return id?.lowercase()?.let { clasesDisponibles[it] }
    }

    fun getAsesinoActual(): Player? {
        // En este juego solo suele haber uno, devolvemos el primero válido
        return asesinosActivos.keys.firstOrNull()?.let { Bukkit.getPlayer(it) }?.takeIf { it.isOnline }
    }

    fun esElAsesino(player: Player?): Boolean = player?.let { asesinosActivos.containsKey(it.uniqueId) } ?: false

    /**
     * Limpieza al apagar el plugin
     */
    fun shutdown() {
        removerTodosLosAsesinos()
        scope.cancel()
    }
}
