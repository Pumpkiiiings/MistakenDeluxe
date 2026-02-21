package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
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
 * DEVESTO - The F3X Architect v2.2
 * FIX: Slots de habilidades (0-3), Knockback manual y carga de items garantizada.
 */
class Devesto : Asesino(
    "devesto",
    Mistaken.instance.configManager.getAsesinos().getString(
        "asesinos.devesto.nombre",
        "<gradient:#4b0082:#000000><b>DEVESTO</b></gradient> <white>[F3X]</white>"
    ) ?: "Devesto"
) {

    private val path = "asesinos.devesto"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val backupMapa = ConcurrentHashMap<Location, Material>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armorKeys.forEach { key ->
            config.getString("$path.armadura.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[key] = it }
            }
        }

        itemKeys.forEach { key ->
            config.getString("$path.items.$key")?.let { id ->
                val name = config.getString("$path.items.${key}_nombre")
                if (id != "none" && id.isNotEmpty()) {
                    CraftEngineUtils.getCustomItem(id)?.let { item ->
                        name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                        itemKitCache[key] = item
                    }
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // 🔥 FIX: Mapping de slots 0, 1, 2, 3 (correspondientes a las teclas 1, 2, 3, 4)
        when (slot) {
            1 -> {
                if (checkCooldown(player, 1)) return
                habilidadMoveTool(player)
                reproducirEfectosHabilidad(player, 1)
            }
            2 -> {
                if (checkCooldown(player, 2)) return
                habilidadPaintTool(player)
                reproducirEfectosHabilidad(player, 2)
            }
            3 -> {
                if (checkCooldown(player, 3)) return
                habilidadResizeTool(player)
                reproducirEfectosHabilidad(player, 3)
            }
            4 -> {
                if (checkCooldown(player, 4)) return
                habilidadDeleteTool(player)
                reproducirEfectosHabilidad(player, 4)
            }
        }
    }

    // --- 🏗️ HABILIDADES CON KNOCKBACK ---

    private fun habilidadMoveTool(player: Player) {
        player.sendMessage(mm.deserialize("<blue>[F3X]</blue> <gray>Herramienta: <white>MOVE"))
        player.playSound(player.location, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 2f)

        player.getNearbyEntities(10.0, 10.0, 10.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                // 🔥 TRUE KNOCKBACK: Empujamos al jugador en la dirección que mira Devesto
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
                // Pequeño empuje para que se note el golpe de "pintura"
                target.velocity = target.location.toVector().subtract(player.location.toVector()).normalize().multiply(0.5).setY(0.2)
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
        player.sendMessage(mm.deserialize("<red><b>//set 0</b></red>"))
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

        // 🔥 TRUE DAMAGE & KNOCKBACK
        player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                plugin.combatManager.processTrueDamage(target, player, 12.0)

                // Explosión que aleja a los sobrevivientes del punto de impacto
                val kb = target.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.5)
                target.velocity = kb
            }
        }
    }

    // --- 🛠️ EQUIPAMIENTO (FIXED) ---
    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // 🔥 FIX: Lazy load para asegurar armadura de CraftEngine
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) {
            preLoadKit()
        }

        // Cargar armadura desde caché con clonación segura
        itemKitCache["casco"]?.let { inv.helmet = it.clone() }
        itemKitCache["pechera"]?.let { inv.chestplate = it.clone() }
        itemKitCache["pantalones"]?.let { inv.leggings = it.clone() }
        itemKitCache["botas"]?.let { inv.boots = it.clone() }

        // Cargar ítems en Hotbar (Slot 0-3 y Arma en 8)
        itemKitCache["habilidad1"]?.let { inv.setItem(1, it.clone()) }
        itemKitCache["habilidad2"]?.let { inv.setItem(2, it.clone()) }
        itemKitCache["habilidad3"]?.let { inv.setItem(3, it.clone()) }
        itemKitCache["habilidad4"]?.let { inv.setItem(4, it.clone()) }
        itemKitCache["arma"]?.let { inv.setItem(8, it.clone()) }

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // --- 🔄 ÓRBITA ---
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
        val radio = 1.3
        entidades[0].teleport(player.location.clone().add(radio * cos(anguloBase), 0.8, radio * sin(anguloBase)))
        entidades[1].teleport(player.location.clone().add(radio * cos(anguloBase + Math.PI), 0.8, radio * sin(anguloBase + Math.PI)))
        angulos[uuid] = anguloBase + 0.12
    }

    private fun crearDisplayHerramienta(loc: Location, item: ItemStack): ItemDisplay {
        return loc.world.spawn(loc, ItemDisplay::class.java) { id ->
            id.setItemStack(item)
            id.transformation = Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf().rotateZ(Math.toRadians(45.0).toFloat()), JomlVector3f(0.7f, 0.7f, 0.7f), Quaternionf())
            id.teleportDuration = 1; id.interpolationDuration = 1
        }
    }

    override fun mostrarTrail(player: Player) {}

    private fun limpiarEntidades(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        if (backupMapa.isNotEmpty()) {
            backupMapa.forEach { (loc, mat) -> loc.block.type = mat }
            backupMapa.clear()
        }
        player?.let { limpiarEntidades(it.uniqueId) }
        super.cleanup(player)
    }
}
