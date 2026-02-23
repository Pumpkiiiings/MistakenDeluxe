package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
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

class ColorAndElectricity : Asesino(
    "colorandelectricity",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.colorandelectricity.nombre", "<gradient:#ff0080:#00ffff:#ffff00><b>色彩電気</b></gradient>", "asesinos_info")
) {

    private val path = "asesinos.colorandelectricity"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val orbitMaterials = listOf(
        Material.PURPLE_WOOL, Material.BLUE_WOOL, Material.LIGHT_BLUE_WOOL,
        Material.LIME_WOOL, Material.SMOOTH_STONE, Material.ORANGE_WOOL, Material.RED_WOOL
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
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
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
            1 -> if (!checkCooldown(player, 1)) { habilidadVividTrace(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadColorDrain(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadPulseStatic(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadShikisaiEnd(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        if (itemKitCache.isEmpty()) preLoadKit()

        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            val namePath = if (key == "arma") "asesinos.colorandelectricity.habilidades_nombres.arma"
            else "asesinos.colorandelectricity.habilidades_nombres.$key"

            val localizedName = langInfo.getString(namePath)
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

        setLocalizedItem(0, "casco", true)
        setLocalizedItem(0, "pechera", true)
        setLocalizedItem(0, "pantalones", true)
        setLocalizedItem(0, "botas", true)

        setLocalizedItem(1, "habilidad1")
        setLocalizedItem(2, "habilidad2")
        setLocalizedItem(3, "habilidad3")
        setLocalizedItem(4, "habilidad4")
        setLocalizedItem(8, "arma")

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    private fun habilidadVividTrace(player: Player) {
        val dir = player.location.direction.normalize().multiply(1.8).setY(0.2)
        player.velocity = dir
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f)

        val job = scope.launch {
            var count = 0
            val hitted = mutableSetOf<UUID>()
            while (isActive && count < 10 && player.isOnline) {
                withContext(plugin.bukkitDispatcher) {
                    player.getNearbyEntities(2.5, 2.5, 2.5).filterIsInstance<Player>().forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim) && !hitted.contains(victim.uniqueId)) {
                            hitted.add(victim.uniqueId)
                            plugin.gameManager.combatManager.takeDamage(victim)
                            victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.4)
                            victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 1.5f)
                        }
                    }
                }
                delay(50)
                count++
            }
        }
        trackJob(job)
    }

    private fun habilidadColorDrain(player: Player) {
        player.playSound(player.location, Sound.BLOCK_CONDUIT_ATTACK_TARGET, 1f, 1.8f)
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                victim.sendMessage(mm.deserialize("<gradient:#ff0080:#00ffff><i>\"Dame tus colores...\"</i></gradient>"))
            }
        }
    }

    private fun habilidadPulseStatic(player: Player) {
        val job = scope.launch {
            var ticks = 0
            while (isActive && ticks <= 3 && player.isOnline) {
                withContext(plugin.bukkitDispatcher) {
                    player.world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, player.location, 20, 2.0, 2.0, 2.0, 0.05)
                    player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            plugin.gameManager.combatManager.takeDamage(victim)
                            victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(0.8).setY(0.3)
                        }
                    }
                }
                delay(250)
                ticks++
            }
        }
        trackJob(job)
    }

    private fun habilidadShikisaiEnd(player: Player) {
        val target = player.getNearbyEntities(15.0, 15.0, 15.0).filterIsInstance<Player>().find { !plugin.asesinoManager.esElAsesino(it) }
        target?.let { t ->
            player.teleportAsync(t.location).thenAccept {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.location, 30, 0.5, 1.0, 0.5, 0.5)
                    t.sendMessage(mm.deserialize("<red><b>[!] SOBRECARGA CROMÁTICA</b></red>"))
                    t.velocity = Vector(0.0, 1.2, 0.0)
                })
            }
        }
    }

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
            bd.teleportDuration = 2; bd.interpolationDuration = 2
        }
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 0.1, 0.0)
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.DUST, ParticleDustData(1f, 0f, 0.5f, 1.1f)), false, Vector3d(loc.x, loc.y, loc.z), Vector3f(0.1f, 0.1f, 0.1f), 0.01f, 1)
        loc.world.players.forEach {
            if (it.location.distanceSquared(loc) < 625.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet)
        }
    }

    private fun limpiarEntidades(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarEntidades(it.uniqueId) }
        scope.coroutineContext.cancelChildren()
    }
}
