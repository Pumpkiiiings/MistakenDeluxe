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

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        // Carga de armadura
        armorKeys.forEach { key ->
            config.getString("$path.armadura.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    // --- FALLBACK VANILLA ---
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
                    itemKitCache[key] = item
                }
            }
        }

        // Carga de items
        itemKeys.forEach { key ->
            config.getString("$path.items.$key")?.let { id ->
                val name = config.getString("$path.items.${key}_nombre")
                if (id != "none" && id.isNotEmpty()) {
                    // --- FALLBACK VANILLA ---
                    // Si CraftEngine devuelve null, intentamos crearlo como material de Minecraft
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)

                    name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                    itemKitCache[key] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // Slot 1 = Tecla 2, Slot 2 = Tecla 3...
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadInkPortal(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadInkFlow(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadInkPuddle(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadTheBeast(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🌀 H1: INK PORTAL ---
    private fun habilidadInkPortal(player: Player) {
        val target = player.getTargetBlockExact(15) ?: player.location.add(player.location.direction.multiply(5)).block
        val targetLoc = target.location.add(0.5, 1.1, 0.5)

        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 40, 4, false, false))

        player.world.spawnParticle(org.bukkit.Particle.SQUID_INK, player.location, 50, 0.5, 1.0, 0.5, 0.1)
        player.playSound(player.location, Sound.ENTITY_SQUID_SQUIRT, 1f, 0.5f)

        scope.launch {
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
    }

    // --- 💦 H2: THE INK FLOW ---
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

    // --- 🕳️ H3: INK PUDDLE ---
    private fun habilidadInkPuddle(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.05, 0.5)
        val puddle = player.world.spawn(loc, BlockDisplay::class.java) {
            it.block = Material.COAL_BLOCK.createBlockData()
            it.transformation = Transformation(JomlVector3f(-0.5f, 0f, -0.5f), Quaternionf(), JomlVector3f(1.5f, 0.05f, 1.5f), Quaternionf())
        }

        scope.launch {
            var duration = 200
            while (duration > 0 && puddle.isValid) {
                withContext(plugin.mainThread) {
                    puddle.world.getNearbyEntities(puddle.location, 1.5, 1.0, 1.5).filterIsInstance<Player>().forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 1))
                        }
                    }
                }
                delay(100); duration--
            }
            withContext(plugin.mainThread) { puddle.remove() }
        }
    }

    // --- 👹 H4: THE BEAST ---
    private fun habilidadTheBeast(player: Player) {
        isBeastMode = true
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 200, 1))
        player.isGlowing = true
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f)

        val job = scope.launch {
            delay(10000)
            withContext(plugin.mainThread) {
                isBeastMode = false
                player.isGlowing = false
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
            }
        }
        trackJob(job)
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Recargar si falta algo
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        // Hotbar (Slot 1 al 4)
        inv.setItem(1, itemKitCache["habilidad1"]?.clone())
        inv.setItem(2, itemKitCache["habilidad2"]?.clone())
        inv.setItem(3, itemKitCache["habilidad3"]?.clone())
        inv.setItem(4, itemKitCache["habilidad4"]?.clone())
        inv.setItem(8, itemKitCache["arma"]?.clone())

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val loc = player.location
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.SQUID_INK), false, Vector3d(loc.x, loc.y + 0.1, loc.z), Vector3f(0.2f, 0.1f, 0.2f), 0.02f, 2)
        loc.world.players.forEach { if (it != player && it.location.distanceSquared(loc) < 400.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet) }
    }

    override fun mostrarTrailFisico(player: Player) {}

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        isBeastMode = false
        scope.coroutineContext.cancelChildren()
    }
}
