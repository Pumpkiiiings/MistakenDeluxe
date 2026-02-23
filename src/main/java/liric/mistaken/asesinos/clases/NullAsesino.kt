package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.mainThread
import org.bukkit.*
import org.bukkit.entity.*
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
 * NullAsesino: El Ente del Glitch.
 * FIX: Soporte Multi-Idioma, Armadura garantizada y Órbita Mística.
 */
class NullAsesino : Asesino(
    "null",
    // Nombre dinámico basado en el idioma default del servidor
    Mistaken.instance.messageConfig.getSpecificFile(null, "asesinos").getString("asesinos.null.nombre", "NULL")!!
) {

    private val pathBase = "asesinos.null"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTraps = ConcurrentHashMap.newKeySet<Entity>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // --- 🧊 OBJETOS MÍSTICOS ORBITANTES ---
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val orbitMaterials = listOf(Material.BEACON, Material.ENDER_EYE, Material.NETHER_STAR)

    init {
        preLoadKit()
    }

    /**
     * 🔥 PRE-LOAD LÓGICO:
     * Carga materiales e IDs del archivo asesinos.yml de la RAIZ.
     */
    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinosConfig(null) // Archivo raíz
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("asesinos.null.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("asesinos.null.items.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // Mapeo de slots: 1, 2, 3, 4 (Teclas 2, 3, 4, 5)
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadErrorRender(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadGeneradorBait(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadPrisionVacio(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadColmillosVacio(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🛠️ EQUIPAMIENTO (SISTEMA MULTI-IDIOMA) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Fix de carga CraftEngine
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        // Obtenemos el archivo de traducción del jugador (lang/es/asesinos.yml)
        val langAsesinos = plugin.messageConfig.getSpecificFile(player, "asesinos")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            // Buscamos el nombre traducido en la carpeta del idioma
            val namePath = if (key == "arma") "asesinos.null.items.arma_nombre"
            else "asesinos.null.items.${key}_nombre"

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

        // 1. Armadura
        setLocalizedItem(0, "casco", true)
        setLocalizedItem(0, "pechera", true)
        setLocalizedItem(0, "pantalones", true)
        setLocalizedItem(0, "botas", true)

        // 2. Hotbar (Slots 1 al 4 y Arma en 8)
        setLocalizedItem(1, "habilidad1")
        setLocalizedItem(2, "habilidad2")
        setLocalizedItem(3, "habilidad3")
        setLocalizedItem(4, "habilidad4")
        setLocalizedItem(8, "arma")

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // --- 🚀 HABILIDADES ---

    private fun habilidadErrorRender(player: Player) {
        player.world.spawnParticle(org.bukkit.Particle.FLASH, player.location.add(0.0, 1.0, 0.0), 3, 0.5, 0.5, 0.5, 0.0)
        player.getNearbyEntities(12.0, 12.0, 12.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 200, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
            }
        }
    }

    private fun habilidadGeneradorBait(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.1, 0.5)
        val bait = loc.world?.spawn(loc, ArmorStand::class.java) { asEntity ->
            asEntity.isVisible = false
            asEntity.isMarker = true
            asEntity.equipment.helmet = ItemStack(Material.BEACON)
        } ?: return
        activeTraps.add(bait)
    }

    private fun habilidadPrisionVacio(player: Player) {
        val ray = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, 15.0) {
            it is Player && !plugin.asesinoManager.esElAsesino(it)
        }
        val victim = ray?.hitEntity as? Player ?: return
        victim.velocity = player.location.toVector().subtract(victim.location.toVector()).normalize().multiply(0.6).setY(0.2)
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 10))
    }

    private fun habilidadColmillosVacio(player: Player) {
        val startLoc = player.location
        val direction = startLoc.direction.setY(0.0).normalize()
        scope.launch {
            val currentLoc = startLoc.clone()
            repeat(15) {
                withContext(plugin.mainThread) {
                    currentLoc.add(direction)
                    currentLoc.world?.spawn(currentLoc, EvokerFangs::class.java)
                    currentLoc.world?.getNearbyEntities(currentLoc, 1.5, 1.5, 1.5)?.filterIsInstance<Player>()?.forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            plugin.combatManager.processTrueDamage(victim, player, 4.0)
                            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                        }
                    }
                }
                delay(50)
            }
        }
    }

    // --- 🧊 MOTOR FÍSICO: ÓRBITA MÍSTICA ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarVisuales(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf<ItemDisplay>().apply {
                orbitMaterials.forEach { mat ->
                    add(player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                        id.setItemStack(ItemStack(mat))
                        id.transformation = Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
                        id.teleportDuration = 2; id.interpolationDuration = 2
                    })
                }
            }
        }

        val anguloActual = (angulos.getOrDefault(uuid, 0.0) + 0.10) % (Math.PI * 2)
        val radio = 1.4
        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / entidades.size) * i
                val x = radio * cos(anguloActual + offset)
                val z = radio * sin(anguloActual + offset)
                val y = 1.1 + (0.2 * sin((anguloActual + offset) * 2))
                display.teleport(player.location.clone().add(x, y, z))
            }
        }
        angulos[uuid] = anguloActual
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 1.1, 0.0)
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.WITCH), false, Vector3d(loc.x, loc.y, loc.z), Vector3f(0.2f, 0.2f, 0.2f), 0.02f, 1)
        player.world.players.forEach { if (it.location.distanceSquared(loc) < 625.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet) }
    }

    private fun limpiarVisuales(uuid: UUID) { orbitadores.remove(uuid)?.forEach { it.remove() }; angulos.remove(uuid) }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarVisuales(it.uniqueId) }
        activeTraps.forEach { it.remove() }; activeTraps.clear()
        scope.coroutineContext.cancelChildren()
    }
}
