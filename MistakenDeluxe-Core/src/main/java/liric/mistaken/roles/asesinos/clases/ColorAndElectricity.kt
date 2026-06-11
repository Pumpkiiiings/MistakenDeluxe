package liric.mistaken.roles.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.roles.asesinos.Asesino
import liric.mistaken.roles.asesinos.CoreAsesino
import liric.mistaken.utils.hooks.CraftEngine
import liric.mistaken.utils.misc.HitboxVisualizer
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
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class ColorAndElectricity : CoreAsesino(
    "colorandelectricity",
    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(null, "asesinos.colorandelectricity.nombre", "asesinos_info")
) {

    private val path = "asesinos.colorandelectricity"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()

    private val orbitMaterials = listOf(
        Material.PURPLE_WOOL, Material.BLUE_WOOL, Material.LIGHT_BLUE_WOOL,
        Material.LIME_WOOL, Material.YELLOW_WOOL, Material.ORANGE_WOOL, Material.RED_WOOL
    )

    init { preLoadKit() }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$path.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("$path.items.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
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
        inv.armorContents = arrayOfNulls(4)

        val configMecanica = plugin.configManager.getAsesinos()
        val langInfo = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("asesinos.colorandelectricity.armadura.$key")
            else configMecanica.getString("asesinos.colorandelectricity.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngine.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "arma") "asesinos.colorandelectricity.habilidades_nombres.arma"
            else "asesinos.colorandelectricity.habilidades_nombres.$key"

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
            } else inv.setItem(slot, item)
        }

        deliver("casco", 0, true); deliver("pechera", 0, true)
        deliver("pantalones", 0, true); deliver("botas", 0, true)
        deliver("habilidad1", 1); deliver("habilidad2", 2)
        deliver("habilidad3", 3); deliver("habilidad4", 4)
        deliver("arma", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    private fun habilidadVividTrace(player: Player) {
        val dir = player.location.direction.normalize().multiply(1.8).setY(0.2)
        player.velocity = dir
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f)

        var count = 0
        val hitted = mutableSetOf<UUID>()

        // 🔥 HITBOX: Caja Dinámica
        val hitbox = HitboxVisualizer.createHitbox(player.location, 2.5, 2.5, 2.5, Material.CYAN_STAINED_GLASS)

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (count >= 10 || !player.isOnline) {
                hitbox?.remove()
                task.cancel()
                return@Consumer
            }

            hitbox?.teleport(player.location) // Actualiza visual

            player.getNearbyEntities(2.5, 2.5, 2.5).filterIsInstance<Player>().forEach { victim ->
                if (esObjetivoValido(player, victim) && hitted.add(victim.uniqueId)) {
                    plugin.combatManager.takeDamage(victim)
                    victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.4)
                    victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 1.5f)
                    hitbox?.block = Material.RED_STAINED_GLASS.createBlockData() // Feedback visual de hit
                }
            }
            count++
        }, null, 1L, 1L)
    }

    private fun habilidadColorDrain(player: Player) {
        player.playSound(player.location, Sound.BLOCK_CONDUIT_ATTACK_TARGET, 1f, 1.8f)

        // 🔥 HITBOX: Caja Instantánea
        HitboxVisualizer.drawInstantHitbox(plugin, player.location, 8.0, 8.0, 8.0, 15L, Material.PURPLE_STAINED_GLASS)

        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (esObjetivoValido(player, victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                victim.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(victim, "roles.killer.abilities.color_and_electricity.dame_tus_colores"))
            }
        }
    }

    private fun habilidadPulseStatic(player: Player) {
        var ticks = 0
        // 🔥 HITBOX: Caja Intermitente (Dura todo el efecto)
        val hitbox = HitboxVisualizer.createHitbox(player.location, 6.0, 6.0, 6.0, Material.YELLOW_STAINED_GLASS)

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks > 3 || !player.isOnline) {
                hitbox?.remove()
                task.cancel()
                return@Consumer
            }

            hitbox?.teleport(player.location) // Sigue al jugador
            player.world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, player.location, 20, 2.0, 2.0, 2.0, 0.05)

            player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { victim ->
                if (esObjetivoValido(player, victim)) {
                    plugin.combatManager.takeDamage(victim)
                    victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(0.8).setY(0.3)
                }
            }
            ticks++
        }, null, 1L, 5L)
    }

    private fun habilidadShikisaiEnd(player: Player) {
        // 🔥 HITBOX: Area de Búsqueda
        HitboxVisualizer.drawInstantHitbox(plugin, player.location, 15.0, 15.0, 15.0, 20L, Material.ORANGE_STAINED_GLASS)

        val target = player.getNearbyEntities(15.0, 15.0, 15.0).filterIsInstance<Player>().find { esObjetivoValido(player, it) }
        target?.let { t ->
            player.teleportAsync(t.location).thenAccept {
                player.scheduler.run(plugin, Consumer { _ ->
                    player.world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.location, 30, 0.5, 1.0, 0.5, 0.5)
                    t.sendMessage(mm.deserialize("<red><b>[!] SOBRECARGA CROMÁTICA</b></red>"))
                    t.velocity = Vector(0.0, 1.2, 0.0)
                }, null)
            }
        }
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarEntidades(uuid); return }

        val playerWorld = player.world
        if (orbitadores[uuid]?.firstOrNull()?.world != playerWorld) limpiarEntidades(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            orbitMaterials.map { mat -> crearBloqueOrbitante(player.location, mat) }.toMutableList()
        }

        val anguloBase = angulos.getOrDefault(uuid, 0.0)
        val radio = 1.4
        val step = (2 * Math.PI) / entidades.size
        val playerLoc = player.location

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val currentAngle = anguloBase + (step * i)
                val x = radio * cos(currentAngle)
                val z = radio * sin(currentAngle)
                val y = 1.0 + (0.3 * sin(currentAngle * 2))

                val targetLoc = playerLoc.clone().add(x, y, z)
                targetLoc.yaw = (currentAngle * 100).toFloat() % 360
                targetLoc.pitch = (currentAngle * 50).toFloat() % 360
                display.teleport(targetLoc)
            }
        }
        angulos[uuid] = anguloBase + 0.15
    }

    private fun crearBloqueOrbitante(loc: Location, mat: Material): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(JomlVector3f(-0.125f, -0.125f, -0.125f), Quaternionf(), JomlVector3f(0.25f, 0.25f, 0.25f), Quaternionf())
            bd.teleportDuration = 3; bd.interpolationDuration = 3
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
    }
}





