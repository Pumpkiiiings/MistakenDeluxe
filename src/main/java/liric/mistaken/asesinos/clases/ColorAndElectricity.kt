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
import liric.mistaken.utils.mainThread
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * ColorAndElectricity - La Entidad Cromática v2.0
 * Sincronizado con YAML y optimizado para Paper 1.21.4
 */
class ColorAndElectricity : Asesino(
    "colorandelectricity",
    Mistaken.Companion.instance.configManager.getAsesinos().getString(
        "asesinos.colorandelectricity.nombre",
        "<gradient:#ff0080:#00ffff:#ffff00><b>色彩電気</b></gradient>"
    )!!
) {

    private val path = "asesinos.colorandelectricity"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // --- 🧊 DISPLAY BLOCKS (Colores del YAML) ---
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
        preLoadKit()
    }

    /**
     * 🔥 CACHÉ DE KIT: Lee el YAML una sola vez.
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
            1 -> habilidadVividTrace(player)
            2 -> habilidadColorDrain(player)
            3 -> habilidadPulseStatic(player)
            4 -> habilidadShikisaiEnd(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- ⚡ HABILIDADES ---

    private fun habilidadVividTrace(player: Player) {
        // Dash cinético (H1: Vivid Trace)
        player.velocity = player.location.direction.multiply(1.8).setY(0.2)
        player.world.spawnParticle(org.bukkit.Particle.WITCH, player.location, 15, 0.3, 0.3, 0.3, 0.1)
    }

    private fun habilidadColorDrain(player: Player) {
        // Robo de visión (H2: Color Drain)
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                victim.sendMessage(mm.deserialize("<gradient:#ff0080:#00ffff><i>\"Dame tus colores...\"</i></gradient>"))
            }
        }
    }

    private fun habilidadPulseStatic(player: Player) {
        // Pulso eléctrico (H3: Pulse Static)
        val runnable = object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks > 3 || !player.isOnline) { cancel(); return }

                player.world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, player.location, 20, 2.0, 2.0, 2.0, 0.05)

                player.getNearbyEntities(5.0, 5.0, 5.0).filterIsInstance<Player>().forEach { victim ->
                    if (!plugin.asesinoManager.esElAsesino(victim)) {
                        // Daño real: 2.0 HP (1 corazón) por pulso
                        plugin.combatManager.processTrueDamage(victim, player, 2.0)
                        victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 40, 1))
                    }
                }
                ticks++
            }
        }
        trackTask(runnable.runTaskTimer(plugin, 0L, 5L))
    }

    private fun habilidadShikisaiEnd(player: Player) {
        // H4: Shikisai End
        val target = player.getNearbyEntities(15.0, 15.0, 15.0)
            .filterIsInstance<Player>()
            .find { !plugin.asesinoManager.esElAsesino(it) }

        target?.let { t ->
            player.teleportAsync(t.location).thenAccept { success ->
                if (success) {
                    player.world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.location, 30, 0.5, 1.0, 0.5, 0.5)
                    t.sendMessage(mm.deserialize("<red><b>[!] SOBRECARGA CROMÁTICA</b></red>"))
                    t.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 120, 0))
                }
            }
        }
    }

    // --- 🌀 MOTOR DE BLOQUES ORBITANTES ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarEntidades(uuid); return }

        // Fix cambio de mundo
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarEntidades(uuid)

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
                val y = 1.1 + (0.15 * sin((anguloBase + offset) * 3))

                display.teleport(player.location.clone().add(x, y, z))
            }
        }
        angulos[uuid] = anguloBase + 0.08
    }

    private fun crearBloqueOrbitante(loc: Location, mat: Material): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                JomlVector3f(-0.1f, -0.1f, -0.1f), Quaternionf(),
                JomlVector3f(0.2f, 0.2f, 0.2f), Quaternionf()
            )
            bd.teleportDuration = 2
            bd.interpolationDuration = 2
        }
    }

    // --- 🚀 TRAIL ASÍNCRONO ---

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 0.1, 0.0)
        val dust = Particle(ParticleTypes.DUST).apply { data = ParticleDustData(1f, 0f, 0.5f, 1.1f) }
        val packet = WrapperPlayServerParticle(dust, false, Vector3d(loc.x, loc.y, loc.z), Vector3f(0.1f, 0.1f, 0.1f), 0.01f, 1)
        loc.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(loc) < 625.0) PacketEvents.getAPI().playerManager.sendPacket(p, packet)
        }
    }

    // --- EQUIPAMIENTO ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Lazy load fix
        if (!itemKitCache.containsKey("casco")) preLoadKit()

        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        inv.setItem(8, itemKitCache["arma"]?.clone())
        inv.setItem(0, itemKitCache["habilidad1"]?.clone())
        inv.setItem(1, itemKitCache["habilidad2"]?.clone())
        inv.setItem(2, itemKitCache["habilidad3"]?.clone())
        inv.setItem(3, itemKitCache["habilidad4"]?.clone())

        player.inventory.heldItemSlot = 8
        player.updateInventory()
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
