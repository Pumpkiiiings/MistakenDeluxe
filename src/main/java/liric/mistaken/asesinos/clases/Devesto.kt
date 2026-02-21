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
import org.bukkit.*
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * DEVESTO - The F3X Architect v2.0
 * Sincronizado con YAML: Carga de nombres, cooldowns y sonidos dinámicos.
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

    /**
     * 🔥 CACHÉ INTELIGENTE:
     * Carga todos los items Y sus nombres desde el YAML una sola vez.
     */
    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armorKeys.forEach { key ->
            config.getString("$path.armadura.$key")?.let { id ->
                if (id != "none") CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[key] = it }
            }
        }

        itemKeys.forEach { key ->
            config.getString("$path.items.$key")?.let { id ->
                val name = config.getString("$path.items.${key}_nombre")
                if (id != "none") {
                    CraftEngineUtils.getCustomItem(id)?.let { item ->
                        name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                        itemKitCache[key] = item
                    }
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return

        when (slot) {
            1 -> habilidadMoveTool(player)
            2 -> habilidadPaintTool(player)
            3 -> habilidadResizeTool(player)
            4 -> habilidadDeleteTool(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 🔄 ÓRBITA DE HERRAMIENTAS (ItemDisplays) ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarEntidades(uuid); return }

        // Fix de cambio de mundo
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarEntidades(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf(
                // Tomamos las herramientas del caché para que se vea igual que su arma
                crearDisplayHerramienta(player.location, itemKitCache["arma"] ?: ItemStack(Material.NETHERITE_AXE)),
                crearDisplayHerramienta(player.location, ItemStack(Material.NETHERITE_HOE)) // Azada para el F3X look
            )
        }

        val anguloBase = angulos.getOrDefault(uuid, 0.0)
        val radio = 1.4

        // Rotación Hacha
        val x1 = radio * cos(anguloBase)
        val z1 = radio * sin(anguloBase)
        entidades[0].teleport(player.location.clone().add(x1, 0.9, z1))

        // Rotación Azada (Contraria)
        val x2 = radio * cos(anguloBase + Math.PI)
        val z2 = radio * sin(anguloBase + Math.PI)
        entidades[1].teleport(player.location.clone().add(x2, 0.9, z2))

        angulos[uuid] = anguloBase + 0.12
    }

    private fun crearDisplayHerramienta(loc: Location, item: ItemStack): ItemDisplay {
        return loc.world.spawn(loc, ItemDisplay::class.java) { id ->
            id.setItemStack(item)
            id.transformation = Transformation(
                org.joml.Vector3f(0f, 0f, 0f),
                Quaternionf().rotateZ(Math.toRadians(45.0).toFloat()),
                org.joml.Vector3f(0.7f, 0.7f, 0.7f),
                Quaternionf()
            )
            id.teleportDuration = 1
            id.interpolationDuration = 1
        }
    }

    // --- 🛠️ HABILIDADES F3X ---

    private fun habilidadMoveTool(player: Player) {
        player.getNearbyEntities(10.0, 10.0, 10.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.velocity = player.location.direction.multiply(2.2).setY(0.4)
            }
        }
    }

    private fun habilidadPaintTool(player: Player) {
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                target.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 80, 1))
            }
        }
    }

    private fun habilidadResizeTool(player: Player) {
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
                        val loc = b.location
                        b.type = Material.AIR

                        b.world.spawnParticle(org.bukkit.Particle.BLOCK_CRUMBLE, loc.add(0.5, 0.5, 0.5), 3, b.blockData)
                    }
                }
            }
        }

        player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                plugin.combatManager.processTrueDamage(target, player, 12.0)
            }
        }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        if (itemKitCache.isEmpty()) preLoadKit()

        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        // Usamos forEach para que sea más legible y seguro
        mapOf(
            8 to "arma", 1 to "habilidad1", 2 to "habilidad2",
            3 to "habilidad3", 4 to "habilidad4"
        ).forEach { (slot, key) ->
            itemKitCache[key]?.let { inv.setItem(slot, it.clone()) }
        }

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    override fun mostrarTrail(player: Player) { /* Devesto no tiene trail de partículas */ }

    private fun limpiarEntidades(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        // Restaurar bloques si es que se usó //set 0
        if (backupMapa.isNotEmpty()) {
            backupMapa.forEach { (loc, mat) -> loc.block.type = mat }
            backupMapa.clear()
        }
        player?.let { limpiarEntidades(it.uniqueId) }
        super.cleanup(player)
    }
}
