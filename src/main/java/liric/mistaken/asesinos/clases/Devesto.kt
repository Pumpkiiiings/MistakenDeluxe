package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.mainThread
import org.bukkit.*
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * DEVESTO - The F3X Architect.
 * Sincronizado con Multi-Lang y optimizado para Paper 1.21.4.
 */
class Devesto : Asesino(
    "devesto",
    Mistaken.Companion.instance.messageConfig.getSpecificFile(null, "asesinos").getString("asesinos.devesto.nombre", "Devesto")!!
) {

    private val path = "asesinos.devesto"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val backupMapa = ConcurrentHashMap<Location, Material>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    /**
     * 🔥 PRE-LOAD LÓGICO:
     * Carga los materiales y IDs del archivo asesinos.yml de la RAIZ.
     */
    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinosConfig(null) // Archivo raíz
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("asesinos.devesto.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("asesinos.devesto.items.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // Mantuve tus slots desplazados: Teclas 2, 3, 4, 5
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadMoveTool(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadPaintTool(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadResizeTool(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadDeleteTool(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🛠️ EQUIPAMIENTO (SISTEMA MULTI-IDIOMA) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Lazy load fix
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        // Obtenemos el archivo de lenguaje del jugador
        val langAsesinos = plugin.messageConfig.getSpecificFile(player, "asesinos")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            // 🔥 Buscamos el nombre traducido en la carpeta del idioma del jugador
            val namePath = if (key == "arma") "asesinos.devesto.items.arma_nombre"
            else "asesinos.devesto.items.${key}_nombre"

            val localizedName = langAsesinos.getString(namePath)
            if (localizedName != null) {
                item.editMeta { it.displayName(mm.deserialize(localizedName)) }
            }

            if (isArmor) {
                when(key) {
                    "casco" -> inv.helmet = item
                    "pechera" -> inv.chestplate = item
                    "pantalones" -> inv.leggings = item
                    "botas" -> inv.boots = item
                }
            } else {
                inv.setItem(slot, item)
            }
        }

        // Entregar armadura
        setLocalizedItem(0, "casco", true)
        setLocalizedItem(0, "pechera", true)
        setLocalizedItem(0, "pantalones", true)
        setLocalizedItem(0, "botas", true)

        // Entregar hotbar (Slots 1, 2, 3, 4 y Arma en 8)
        setLocalizedItem(1, "habilidad1")
        setLocalizedItem(2, "habilidad2")
        setLocalizedItem(3, "habilidad3")
        setLocalizedItem(4, "habilidad4")
        setLocalizedItem(8, "arma")

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // --- 🏗️ HABILIDADES ---

    private fun habilidadMoveTool(player: Player) {
        player.sendMessage(mm.deserialize("<blue>[F3X]</blue> <gray>Herramienta: <white>MOVE"))
        player.getNearbyEntities(10.0, 10.0, 10.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                val push = player.location.direction.normalize().multiply(2.2).setY(0.4)
                target.velocity = push
            }
        }
    }

    private fun habilidadPaintTool(player: Player) {
        player.sendMessage(mm.deserialize("<blue>[F3X]</blue> <gray>Herramienta: <white>PAINT"))
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                target.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 80, 1))
            }
        }
    }

    private fun habilidadResizeTool(player: Player) {
        player.sendMessage(mm.deserialize("<blue>[F3X]</blue> <gray>Herramienta: <white>RESIZE"))
        player.getNearbyEntities(12.0, 12.0, 12.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, 35, 2))
            }
        }
    }

    private fun habilidadDeleteTool(player: Player) {
        val targetBlock = player.getTargetBlockExact(5)
        if (targetBlock != null && !targetBlock.type.isAir) {
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        val b = targetBlock.getRelative(x, y, z)
                        if (b.type.isAir || b.type == Material.BEDROCK || b.type == Material.BARRIER) continue
                        backupMapa.putIfAbsent(b.location, b.type)
                        b.type = Material.AIR
                        b.world.spawnParticle(org.bukkit.Particle.BLOCK_CRUMBLE, b.location.add(0.5, 0.5, 0.5), 3, b.blockData)
                    }
                }
            }
        }
        player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                plugin.combatManager.processTrueDamage(target, player, 12.0) // Daño real 6 corazones
                val kb = target.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.5)
                target.velocity = kb
            }
        }
    }

    // --- 🔄 ÓRBITA Y TRAIL ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarEntidades(uuid); return }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarEntidades(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf(
                crearDisplayHerramienta(player.location, itemKitCache["arma"] ?: ItemStack(Material.NETHERITE_AXE)),
                crearDisplayHerramienta(player.location, ItemStack(Material.NETHERITE_HOE))
            )
        }

        val anguloBase = angulos.getOrDefault(uuid, 0.0)
        entidades[0].teleport(player.location.clone().add(1.3 * cos(anguloBase), 0.8, 1.3 * sin(anguloBase)))
        entidades[1].teleport(player.location.clone().add(1.3 * cos(anguloBase + Math.PI), 0.8, 1.3 * sin(anguloBase + Math.PI)))
        angulos[uuid] = anguloBase + 0.12
    }

    private fun crearDisplayHerramienta(loc: Location, item: ItemStack): ItemDisplay {
        return loc.world.spawn(loc, ItemDisplay::class.java) { id ->
            id.setItemStack(item)
            id.transformation = Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf().rotateZ(Math.toRadians(45.0).toFloat()), JomlVector3f(0.6f, 0.6f, 0.6f), Quaternionf())
            id.teleportDuration = 1; id.interpolationDuration = 1
        }
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.ENCHANT), false, Vector3d(loc.x, loc.y + 1.2, loc.z), Vector3f(0.4f, 0.4f, 0.4f), 0.1f, 2)
        loc.world.players.forEach { if (it.location.distanceSquared(loc) < 625.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet) }
    }

    private fun limpiarEntidades(uuid: UUID) { orbitadores.remove(uuid)?.forEach { it.remove() }; angulos.remove(uuid) }

    override fun cleanup(player: Player?) {
        if (backupMapa.isNotEmpty()) {
            backupMapa.forEach { (loc, mat) -> loc.block.type = mat }
            backupMapa.clear()
        }
        player?.let { limpiarEntidades(it.uniqueId) }
        super.cleanup(player)
    }
}
