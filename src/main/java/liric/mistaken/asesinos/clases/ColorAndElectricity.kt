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
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

class ColorAndElectricity : Asesino(
    "colorandelectricity",
    Mistaken.Companion.instance.configManager.getAsesinos().getString(
        "asesinos.colorandelectricity.nombre",
        "<gradient:#ff0080:#00ffff:#ffff00><b>色彩電気</b></gradient>"
    )!!
) {

    private val path = "asesinos.colorandelectricity"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()

    private val orbitMaterials = listOf(
        Material.PURPLE_WOOL, Material.BLUE_WOOL, Material.LIGHT_BLUE_WOOL,
        Material.LIME_WOOL, Material.SMOOTH_STONE, Material.ORANGE_WOOL, Material.RED_WOOL
    )

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armorKeys.forEach { key ->
            config.getString("$path.armadura.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[key] = it }
                }
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
        // 🔥 NUEVO MAPPING DESPLAZADO UN SLOT A LA DERECHA
        // Slot 1 (Tecla 2) = H1, Slot 2 (Tecla 3) = H2, Slot 3 (Tecla 4) = H3, Slot 4 (Tecla 5) = H4
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadVividTrace(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadColorDrain(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadPulseStatic(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadShikisaiEnd(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🚀 HABILIDADES REPARADAS ---

    private fun habilidadVividTrace(player: Player) {
        // Dash potente
        val dir = player.location.direction.normalize().multiply(1.8).setY(0.2)
        player.velocity = dir

        // 🔥 FIX HABILIDAD 1: Detección de impacto continua durante el dash (8 ticks)
        val task = object : BukkitRunnable() {
            var count = 0
            val hitted = mutableSetOf<UUID>()

            override fun run() {
                if (count >= 8 || !player.isOnline) { cancel(); return }

                player.getNearbyEntities(2.5, 2.5, 2.5).filterIsInstance<Player>().forEach { victim ->
                    if (!plugin.asesinoManager.esElAsesino(victim) && !hitted.contains(victim.uniqueId)) {
                        hitted.add(victim.uniqueId)

                        // Daño Real
                        plugin.combatManager.processTrueDamage(victim, player, 4.0)

                        // 🔥 TRUE KNOCKBACK: Empuje explosivo hacia afuera
                        val kb = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.5)
                        victim.velocity = kb

                        victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 1.5f)
                    }
                }
                count++
            }
        }
        task.runTaskTimer(plugin, 0L, 1L)

        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f)
        player.world.spawnParticle(org.bukkit.Particle.WITCH, player.location, 15, 0.3, 0.3, 0.3, 0.1)
    }

    private fun habilidadColorDrain(player: Player) {
        player.playSound(player.location, Sound.BLOCK_CONDUIT_ATTACK_TARGET, 1f, 1.8f)
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                // Pequeño sacudón visual
                victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(0.2).setY(0.1)
            }
        }
    }

    private fun habilidadPulseStatic(player: Player) {
        val runnable = object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks > 3 || !player.isOnline) { cancel(); return }
                player.world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, player.location, 20, 2.0, 2.0, 2.0, 0.05)
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1.5f + (ticks * 0.2f))

                player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { victim ->
                    if (!plugin.asesinoManager.esElAsesino(victim)) {
                        plugin.combatManager.processTrueDamage(victim, player, 2.0)

                        // 🔥 TRUE KNOCKBACK (Repulsión eléctrica)
                        val push = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(0.8).setY(0.3)
                        victim.velocity = push

                        victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 40, 1))
                    }
                }
                ticks++
            }
        }
        trackTask(runnable.runTaskTimer(plugin, 0L, 5L))
    }

    private fun habilidadShikisaiEnd(player: Player) {
        val target = player.getNearbyEntities(15.0, 15.0, 15.0).filterIsInstance<Player>()
            .find { !plugin.asesinoManager.esElAsesino(it) }

        target?.let { t ->
            player.teleportAsync(t.location).thenAccept {
                player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 0.5f)
                player.world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.location, 30, 0.5, 1.0, 0.5, 0.5)
                t.sendMessage(mm.deserialize("<red><b>[!] SOBRECARGA CROMÁTICA</b></red>"))

                // 🔥 KNOCKBACK VERTICAL (Hacia arriba)
                t.velocity = Vector(0.0, 1.2, 0.0)
                t.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 120, 0))
            }
        }
    }

    // --- 🛠️ EQUIPAMIENTO (FIXED) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Si el caché está vacío (CraftEngine timing), forzamos carga
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) {
            preLoadKit()
        }

        // Armadura
        itemKitCache["casco"]?.let { inv.helmet = it.clone() }
        itemKitCache["pechera"]?.let { inv.chestplate = it.clone() }
        itemKitCache["pantalones"]?.let { inv.leggings = it.clone() }
        itemKitCache["botas"]?.let { inv.boots = it.clone() }

        // Items
        itemKitCache["habilidad1"]?.let { inv.setItem(1, it.clone()) }
        itemKitCache["habilidad2"]?.let { inv.setItem(2, it.clone()) }
        itemKitCache["habilidad3"]?.let { inv.setItem(3, it.clone()) }
        itemKitCache["habilidad4"]?.let { inv.setItem(4, it.clone()) }
        itemKitCache["arma"]?.let { inv.setItem(8, it.clone()) }

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // --- 🌀 MOTOR DE BLOQUES ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarEntidades(uuid); return }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarEntidades(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            orbitMaterials.map { mat -> crearBloqueOrbitante(player.location, mat) }.toMutableList()
        }

        val anguloBase = angulos.getOrDefault(uuid, 0.0)
        val radio = 1.6
        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / entidades.size) * i
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
            bd.transformation = Transformation(JomlVector3f(-0.1f, -0.1f, -0.1f), Quaternionf(), JomlVector3f(0.25f, 0.25f, 0.25f), Quaternionf())
            bd.teleportDuration = 2
            bd.interpolationDuration = 2
        }
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 0.1, 0.0)
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.DUST, ParticleDustData(1f, 0f, 0.5f, 1.1f)), false, Vector3d(loc.x, loc.y, loc.z), Vector3f(0.1f, 0.1f, 0.1f), 0.01f, 1)
        loc.world.players.forEach { p -> if (p != player && p.location.distanceSquared(loc) < 625.0) PacketEvents.getAPI().playerManager.sendPacket(p, packet) }
    }

    private fun limpiarEntidades(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiarEntidades(it.uniqueId)
            it.removePotionEffect(PotionEffectType.DARKNESS)
        }
    }
}
