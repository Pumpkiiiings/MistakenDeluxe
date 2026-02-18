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
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoManager: El motor central de las entidades del mal.
 * Optimizado para Paper 1.21.4 con equipamiento distribuido mediante Coroutines.
 */
class AsesinoManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val asesinosActivos = ConcurrentHashMap<UUID, Asesino>()
    private val clasesDisponibles = ConcurrentHashMap<String, Asesino>()

    init {
        // Registro masivo optimizado
        listOf(
            Slasher(), Herobrine(), Entity303(), NullAsesino()
        ).forEach { registrarClase(it) }
    }

    private fun registrarClase(asesino: Asesino) {
        clasesDisponibles[asesino.id.lowercase()] = asesino
    }

    /**
     * Sincroniza la clase sin efectos visuales. Útil para pre-selección en Lobby.
     */
    fun actualizarAsesino(player: Player, claseId: String) {
        if (claseId.equals("none", ignoreCase = true)) {
            removerAsesino(player)
            return
        }

        val clase = getClasePorId(claseId) ?: return
        clase.cleanup(player)

        plugin.logger.info("${player.name} sincronizado con ${clase.nombre}")
    }

    /**
     * ¡La transformación final!
     * Optimizado: Usa Coroutines para distribuir la carga de equipamiento en varios ticks.
     */
    fun registrarAsesino(player: Player, asesino: Asesino) {
        val uuid = player.uniqueId

        // Limpieza inmediata (Sync)
        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4)
        asesinosActivos[uuid] = asesino

        // Feedback inmediato
        player.sendMessage(plugin.messageConfig.getMessage(player, "killer.transform",
            Placeholder.component("name", mm.deserialize(asesino.nombre))))
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)

        // Motor de equipamiento asíncrono
        scope.launch {
            // 1. Espera de seguridad para evitar conflictos con el mundo (10 ticks = 500ms)
            delay(500L)

            if (!player.isOnline || !asesinosActivos.containsKey(uuid)) return@launch

            // Volvemos al Main Thread para efectos físicos
            withContext(Dispatchers.Main) {
                asesino.equipar(player)
                asesino.mostrarTrail(player)
            }

            // 2. Equipamiento de armadura (5 ticks después)
            delay(250L)

            if (player.isOnline) {
                withContext(Dispatchers.Main) {
                    autoEquiparArmadura(player)
                    player.inventory.heldItemSlot = 8
                    player.updateInventory()
                }
            }
        }
    }

    /**
     * Motor de auto-equipamiento inteligente.
     * Evita loops redundantes y usa comparaciones de String optimizadas.
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

    fun equiparAsesino(player: Player, claseId: String) {
        val clase = getClasePorId(claseId) ?: getClasePorId("slasher")
        clase?.let { registrarAsesino(player, it) }
    }

    fun removerAsesino(player: Player) {
        val asesino = asesinosActivos.remove(player.uniqueId)

        // Si el asesino estaba activo, cleanup específico. Si no, cleanup preventivo de todas las clases.
        asesino?.cleanup(player) ?: clasesDisponibles.values.forEach { it.cleanup(player) }

        // Reset de atributos Paper 1.21.4
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.health = 20.0
        player.isGlowing = false
        player.isSwimming = false
    }

    fun removerTodosLosAsesinos() {
        asesinosActivos.forEach { (uuid, asesino) ->
            Bukkit.getPlayer(uuid)?.let { asesino.cleanup(it) }
        }
        asesinosActivos.clear()
        // Cancelar todas las corrutinas de equipamiento en curso
        scope.coroutineContext.cancelChildren()
    }

    // --- GETTERS MAESTROS ---

    fun getAsesinoDelJugador(player: Player?): Asesino? {
        return player?.let { asesinosActivos[it.uniqueId] }
    }

    fun getClasePorId(id: String?): Asesino? {
        return id?.lowercase()?.let { clasesDisponibles[it] }
    }

    fun getAsesinoActual(): Player? {
        return asesinosActivos.keys.firstOrNull()?.let { Bukkit.getPlayer(it) }?.takeIf { it.isOnline }
    }

    fun getClasesDisponibles(): Map<String, Asesino> = Collections.unmodifiableMap(clasesDisponibles)

    fun esElAsesino(player: Player?): Boolean = player?.let { asesinosActivos.containsKey(it.uniqueId) } ?: false

    /**
     * Limpieza total al apagar el plugin
     */
    fun shutdown() {
        removerTodosLosAsesinos()
        scope.cancel()
    }
}
