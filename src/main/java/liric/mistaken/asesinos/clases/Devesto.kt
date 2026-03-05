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
 * [LIRIC-MISTAKEN 2.0]
 * Devesto: El Arquitecto.
 * FIX: Animación Fluida y Materiales de Construcción (F3X).
 */
class Devesto : Asesino(
    "devesto",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.devesto.nombre", "<gradient:#4b0082:#000000><b>DEVESTO</b></gradient>", "asesinos_info")
) {

    private val path = "asesinos.devesto"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val backupMapa = ConcurrentHashMap<Location, Material>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 🔥 ÍTEMS DE ÓRBITA ESPECÍFICOS
    private val toolMaterials = listOf(
        Material.REPEATING_COMMAND_BLOCK, // Morado
        Material.WOODEN_AXE,
        Material.STRUCTURE_BLOCK
    )

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$path.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("$path.items.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadMoveTool(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadPaintTool(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadResizeTool(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadDeleteTool(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🛠️ EQUIPAMIENTO ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val configMecanica = plugin.configManager.getAsesinos()
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("asesinos.devesto.armadura.$key")
            else configMecanica.getString("asesinos.devesto.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "arma") "asesinos.devesto.habilidades_nombres.arma"
            else "asesinos.devesto.habilidades_nombres.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
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

        deliver("casco", 0, true); deliver("pechera", 0, true)
        deliver("pantalones", 0, true); deliver("botas", 0, true)
        deliver("habilidad1", 1); deliver("habilidad2", 2)
        deliver("habilidad3", 3); deliver("habilidad4", 4)
        deliver("arma", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // --- HABILIDADES DE F3X ---

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
            // RegionScheduler para modificar bloques
            plugin.server.regionScheduler.execute(plugin, targetBlock.location) {
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
        }

        player.world.getNearbyPlayers(player.location, 6.0).forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                plugin.gameManager.combatManager.takeDamage(victim)
                val kb = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.5)
                victim.velocity = kb
            }
        }
    }

    // --- 🔥 ANIMACIÓN F3X ULTRA-FLUIDA ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarEntidades(uuid); return }

        val playerWorld = player.world
        if (orbitadores[uuid]?.firstOrNull()?.world != playerWorld) limpiarEntidades(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            toolMaterials.map { mat -> crearDisplayHerramienta(player.location, mat) }.toMutableList()
        }

        val anguloActual = angulos.getOrDefault(uuid, 0.0)

        // Variables pre-calculadas
        val radio = 1.6
        val step = (2 * Math.PI) / entidades.size
        val playerLoc = player.location

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val currentAngle = anguloActual + (step * i)

                val x = radio * cos(currentAngle)
                val z = radio * sin(currentAngle)
                // Altura estable pero flotante
                val y = 1.2 + (0.15 * sin(currentAngle * 2))

                val targetLoc = playerLoc.clone().add(x, y, z)

                // Rotación dinámica sobre su eje Y
                targetLoc.yaw = (currentAngle * 100).toFloat() % 360

                display.teleport(targetLoc)
            }
        }
        angulos[uuid] = anguloActual + 0.12 // Velocidad de giro
    }

    private fun crearDisplayHerramienta(loc: Location, mat: Material): ItemDisplay {
        return loc.world.spawn(loc, ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(mat))

            // Hacha en Vertical, Bloques normales
            val rotation = if (mat == Material.WOODEN_AXE) {
                Quaternionf().rotateZ(Math.toRadians(45.0).toFloat()) // Inclinación para que se vea vertical
            } else {
                Quaternionf() // Bloques rectos
            }

            id.transformation = Transformation(
                JomlVector3f(0f, 0f, 0f),
                rotation,
                JomlVector3f(0.7f, 0.7f, 0.7f), // Escala grande para que se vean bien
                Quaternionf()
            )
            // 🔥 TRUCO DE FLUIDEZ
            id.teleportDuration = 3
            id.interpolationDuration = 3
        }
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 1.2, 0.0)
        val packet = WrapperPlayServerParticle(
            Particle(ParticleTypes.ENCHANT), false,
            Vector3d(loc.x, loc.y, loc.z),
            Vector3f(0.4f, 0.4f, 0.4f),
            0.1f,
            2
        )
        loc.world.players.forEach {
            if (it.location.distanceSquared(loc) < 625.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet)
        }
    }

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
        scope.coroutineContext.cancelChildren()
    }
}
