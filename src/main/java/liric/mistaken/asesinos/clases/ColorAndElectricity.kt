package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
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

class ColorAndElectricity : Asesino(
    "colorandelectricity",
    Mistaken.instance.configManager.getAsesinos().getString(
        "asesinos.colorandelectricity.nombre",
        "<gradient:#ff0080:#00ffff:#ffff00><b>色彩電気</b></gradient>"
    ) ?: "Color and Electricity"
) {

    private val path = "asesinos.colorandelectricity"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // --- 🧊 DISPLAY BLOCKS (Configuración de colores solicitada) ---
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val orbitMaterials = listOf(
        Material.PURPLE_WOOL,      // Morado
        Material.BLUE_WOOL,        // Azul Oscuro
        Material.LIGHT_BLUE_WOOL,  // Azul Claro
        Material.LIME_WOOL,        // Lima
        Material.SMOOTH_STONE,     // Piedra Lisa
        Material.ORANGE_WOOL,      // Naranja
        Material.RED_WOOL          // Rojo
    )

    init {
        // Intentamos precargar, pero el método equipar ahora es más inteligente
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armorKeys.forEach { key ->
            val id = config.getString("$path.armadura.$key")
            if (!id.isNullOrEmpty() && id != "none") {
                CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[key] = it }
            }
        }

        itemKeys.forEach { key ->
            val id = config.getString("$path.items.$key")
            if (!id.isNullOrEmpty() && id != "none") {
                CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[key] = it }
            }
        }
    }

    /**
     * 🔥 EQUIPAR CORREGIDO
     * Si el caché está vacío (porque los ítems no estaban listos al iniciar),
     * intenta recargarlos una última vez antes de dar el kit.
     */
    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Si el caché de armadura está vacío, intentamos cargar de nuevo (Fix para CraftEngine)
        if (!itemKitCache.containsKey("casco")) {
            preLoadKit()
        }

        // Equipar Armadura
        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        // Equipar Items
        inv.setItem(8, itemKitCache["arma"]?.clone())
        inv.setItem(1, itemKitCache["habilidad1"]?.clone())
        inv.setItem(2, itemKitCache["habilidad2"]?.clone())
        inv.setItem(3, itemKitCache["habilidad3"]?.clone())
        inv.setItem(4, itemKitCache["habilidad4"]?.clone())

        player.inventory.heldItemSlot = 8
        player.updateInventory()

        // Log para debug si sigue fallando (solo saldrá en consola)
        if (inv.helmet == null) {
            plugin.logger.warning("¡Cuidado! No se pudo cargar la armadura de ColorAndElectricity. Revisa si los IDs de CraftEngine son correctos.")
        }
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) {
            limpiarEntidades(uuid)
            return
        }

        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) {
            limpiarEntidades(uuid)
        }

        val entidades = orbitadores.getOrPut(uuid) {
            orbitMaterials.map { mat -> crearBloqueOrbitante(player.location, mat) }.toMutableList()
        }

        val anguloBase = angulos.getOrDefault(uuid, 0.0)
        val radio = 1.6
        val size = entidades.size

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / size) * i
                val x = radio * cos(anguloBase + offset)
                val z = radio * sin(anguloBase + offset)
                val y = 1.1 + (0.15 * sin((anguloBase + offset) * 3)) // Movimiento ondulado suave

                display.teleport(player.location.clone().add(x, y, z))
            }
        }
        angulos[uuid] = anguloBase + 0.08 // Velocidad de rotación
    }

    private fun crearBloqueOrbitante(loc: Location, mat: Material): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                JomlVector3f(-0.1f, -0.1f, -0.1f), // Centrado perfecto
                Quaternionf(),
                JomlVector3f(0.2f, 0.2f, 0.2f), // Tamaño pequeño tipo "orbe"
                Quaternionf()
            )
            bd.teleportDuration = 2
            bd.interpolationDuration = 2
        }
    }

    // --- EL RESTO DE FUNCIONES SE MANTRENIEN IGUAL ---

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        when (slot) {
            1 -> habilidadVividTrace(player)
            2 -> habilidadColorDrain(player)
            4 -> habilidadShikisaiEnd(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 0.1, 0.0)
        val dust = Particle(ParticleTypes.DUST).apply { data = ParticleDustData(1f, 0f, 0.5f, 1.1f) }
        val packet = WrapperPlayServerParticle(dust, false, Vector3d(loc.x, loc.y, loc.z), Vector3f(0.1f, 0.1f, 0.1f), 0.01f, 1)
        loc.world.players.forEach { p -> if (p != player && p.location.distanceSquared(loc) < 625.0) PacketEvents.getAPI().playerManager.sendPacket(p, packet) }
    }

    private fun habilidadVividTrace(player: Player) {
        player.velocity = player.location.direction.multiply(1.8).setY(0.2)
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f)
    }

    private fun habilidadColorDrain(player: Player) {
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
        }
    }

    private fun habilidadShikisaiEnd(player: Player) {
        val target = player.getNearbyEntities(15.0, 15.0, 15.0).filterIsInstance<Player>().find { !plugin.asesinoManager.esElAsesino(it) }
        target?.let { player.teleportAsync(it.location) }
    }

    private fun limpiarEntidades(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarEntidades(it.uniqueId) }
    }
}
