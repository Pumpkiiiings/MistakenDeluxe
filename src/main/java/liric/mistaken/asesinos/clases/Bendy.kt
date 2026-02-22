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

class Bendy : Asesino(
    "bendy",
    Mistaken.instance.configManager.getAsesinos().getString("asesinos.bendy.nombre", "<black><b>THE INK DEMON</b></black>")!!
) {

    private val path = "asesinos.bendy"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isBeastMode = false
    private val auras = ConcurrentHashMap<UUID, BlockDisplay>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$path.armadura.$k")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("$path.items.$k")?.let { id ->
                val name = config.getString("$path.items.${k}_nombre")
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) {
                habilidadInkPortal(player)
                reproducirEfectosHabilidad(player, 1)
                applyInkFatigue(player)
            }
            2 -> if (!checkCooldown(player, 2)) {
                habilidadInkFlow(player)
                reproducirEfectosHabilidad(player, 2)
                applyInkFatigue(player)
            }
            3 -> if (!checkCooldown(player, 3)) {
                habilidadInkPuddle(player)
                reproducirEfectosHabilidad(player, 3)
                applyInkFatigue(player)
            }
            4 -> if (!checkCooldown(player, 4)) {
                habilidadTheBeast(player)
                reproducirEfectosHabilidad(player, 4)
            }
        }
    }

    private fun applyInkFatigue(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false, true))
    }

    // --- 🌀 HABILIDADES ---

    private fun habilidadInkPortal(player: Player) {
        val target = player.getTargetBlockExact(15) ?: player.location.add(player.location.direction.multiply(5)).block
        val targetLoc = target.location.add(0.5, 1.1, 0.5)

        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 40, 4, false, false))

        player.world.spawnParticle(org.bukkit.Particle.SQUID_INK, player.location, 50, 0.5, 1.0, 0.5, 0.1)
        player.playSound(player.location, Sound.ENTITY_SQUID_SQUIRT, 1f, 0.5f)

        val job = scope.launch {
            delay(1500)
            withContext(plugin.mainThread) {
                if (player.isOnline) {
                    player.teleport(targetLoc)
                    player.world.spawnParticle(org.bukkit.Particle.SQUID_INK, player.location, 50, 0.5, 1.0, 0.5, 0.1)
                    player.playSound(player.location, Sound.ENTITY_SQUID_DEATH, 1f, 0.1f)

                    player.getNearbyEntities(4.0, 4.0, 4.0).filterIsInstance<Player>().forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.1f)
                            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                        }
                    }
                }
            }
        }
        trackJob(job)
    }

    private fun habilidadInkFlow(player: Player) {
        player.playSound(player.location, Sound.ENTITY_GENERIC_SPLASH, 1f, 0.5f)
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                val toVictim = victim.location.toVector().subtract(player.location.toVector()).normalize()
                if (player.location.direction.dot(toVictim) > 0.7) {
                    victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 0))
                }
            }
        }
    }

    private fun habilidadInkPuddle(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.05, 0.5)
        val puddle = player.world.spawn(loc, BlockDisplay::class.java) {
            it.block = Material.BLACK_CONCRETE.createBlockData()
            it.transformation = Transformation(JomlVector3f(-0.75f, 0f, -0.75f), Quaternionf(), JomlVector3f(1.5f, 0.02f, 1.5f), Quaternionf())
        }
        val job = scope.launch {
            var duration = 200
            while (duration > 0 && puddle.isValid) {
                withContext(plugin.mainThread) {
                    puddle.world.getNearbyEntities(puddle.location, 1.0, 1.0, 1.0).filterIsInstance<Player>().forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 1))
                    }
                }
                delay(100); duration--
            }
            withContext(plugin.mainThread) { puddle.remove() }
        }
        trackJob(job)
    }

    private fun habilidadTheBeast(player: Player) {
        isBeastMode = true
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 200, 1))
        player.isGlowing = true
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f)
        val job = scope.launch {
            delay(10000)
            withContext(plugin.mainThread) {
                isBeastMode = false; player.isGlowing = false
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                applyInkFatigue(player)
            }
        }
        trackJob(job)
    }

    // --- 🚀 MOTOR DE PARTÍCULAS (ASYNC) ---

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val loc = player.location
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val mgr = PacketEvents.getAPI().playerManager

        // 1. Rastro de tinta en los pies
        val groundPacket = WrapperPlayServerParticle(
            Particle(ParticleTypes.SQUID_INK), false,
            pos.add(0.0, 0.1, 0.0), Vector3f(0.3f, 0.0f, 0.3f), 0.02f, 2
        )

        // 2. Tinta goteando desde arriba
        val dripPacket = WrapperPlayServerParticle(
            Particle(ParticleTypes.LARGE_SMOKE),
            false, pos.add(0.0, 1.8, 0.0),
            Vector3f(0.2f, 0.4f, 0.2f), 0.01f, 1
        )

        // 🔥 FIX: Eliminado el filtro para que el asesino también vea su propio trail
        loc.world.players.forEach { viewer ->
            if (viewer.location.distanceSquared(loc) < 400.0) {
                mgr.sendPacket(viewer, groundPacket)
                mgr.sendPacket(viewer, dripPacket)
            }
        }
    }

    /**
     * 🔥 EFECTO DE CHARCO DINÁMICO (SYNC)
     */
    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarAura(uuid); return }
        if (auras[uuid]?.world != player.world) limpiarAura(uuid)

        val display = auras.getOrPut(uuid) {
            player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                bd.block = Material.BLACK_CONCRETE.createBlockData()
                bd.transformation = Transformation(JomlVector3f(-0.6f, 0.01f, -0.6f), Quaternionf(), JomlVector3f(1.2f, 0.02f, 1.2f), Quaternionf())
                bd.teleportDuration = 2; bd.interpolationDuration = 2
            }
        }
        display.teleport(player.location.clone().add(0.0, 0.01, 0.0))
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        inv.setItem(1, itemKitCache["habilidad1"]?.clone())
        inv.setItem(2, itemKitCache["habilidad2"]?.clone())
        inv.setItem(3, itemKitCache["habilidad3"]?.clone())
        inv.setItem(4, itemKitCache["habilidad4"]?.clone())
        inv.setItem(8, itemKitCache["arma"]?.clone())

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    private fun limpiarAura(uuid: UUID) { auras.remove(uuid)?.remove() }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        isBeastMode = false
        player?.let { limpiarAura(it.uniqueId) }
        scope.coroutineContext.cancelChildren()
    }
}
