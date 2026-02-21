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
import liric.mistaken.utils.mainThread
import org.bukkit.*
import org.bukkit.attribute.Attribute
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
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * KasaneTeto - La Quimera de las Baguettes v2.0
 * FIX: Slots de habilidades corregidos (0-3) y carga de armadura blindada.
 */
class KasaneTeto : Asesino(
    "teto",
    Mistaken.instance.configManager.getAsesinos().getString(
        "asesinos.teto.nombre",
        "<gradient:#ff66cc:#ff0000><b>KASANE TETO</b></gradient>"
    ) ?: "Kasane Teto"
) {

    private val path = "asesinos.teto"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // --- 🧊 BAGUETTES ORBITANTES ---
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()

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
        // 🔥 FIX: Mapeo de slots 0, 1, 2, 3 para las 4 habilidades
        when (slot) {
            1 -> {
                if (checkCooldown(player, 1)) return
                habilidadTaladro(player)
                reproducirEfectosHabilidad(player, 1)
            }
            2 -> {
                if (checkCooldown(player, 2)) return
                habilidadBaguette(player)
                reproducirEfectosHabilidad(player, 2)
            }
            3 -> {
                if (checkCooldown(player, 3)) return
                habilidadBroma(player)
                reproducirEfectosHabilidad(player, 3)
            }
            4 -> {
                if (checkCooldown(player, 4)) return
                habilidadTerritory(player)
                reproducirEfectosHabilidad(player, 4)
            }
        }
    }

    // --- 🥖 HABILIDADES ---

    private fun habilidadTaladro(player: Player) {
        player.getNearbyEntities(3.5, 3.5, 3.5).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                // Daño Real: 5 HP (2.5 corazones)
                plugin.combatManager.processTrueDamage(victim, player, 5.0)
                victim.addPotionEffect(PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 1))
                victim.playSound(victim.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f)
            }
        }
        player.world.spawnParticle(org.bukkit.Particle.CRIT, player.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.3, 0.3, 0.1)
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 30, 1))
    }

    private fun habilidadBaguette(player: Player) {
        // Dash de Baguette
        player.velocity = player.location.direction.multiply(1.1).setY(0.2)
        player.getNearbyEntities(2.5, 2.5, 2.5).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                val push = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.2)
                victim.velocity = push
            }
        }
        player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 40, 0))
    }

    private fun habilidadBroma(player: Player) {
        // Broma del 1 de Abril
        player.world.spawnParticle(org.bukkit.Particle.EXPLOSION, player.location, 1)
        player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.sendMessage(mm.deserialize("<gradient:#ff66cc:#ff0000><b>¡BAKA!</b> ¿Te la creíste?</gradient>"))
            }
        }
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 25, 4))
    }

    private fun habilidadTerritory(player: Player) {
        // Buff Territory
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 240, 0))
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 240, 1))
        player.isGlowing = true

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                player.isGlowing = false
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 2))
                player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f)
            }
        }, 240L)
    }

    // --- 🚀 VISUALES ---

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val loc = player.location
        val pos = Vector3d(loc.x, loc.y + 0.2, loc.z)
        val pinkPacket = WrapperPlayServerParticle(Particle(ParticleTypes.SPORE_BLOSSOM_AIR), false, pos, Vector3f(0.3f, 0.1f, 0.3f), 0.01f, 1)
        loc.world.players.forEach { p -> if (p != player && p.location.distanceSquared(loc) < 400.0) PacketEvents.getAPI().playerManager.sendPacket(p, pinkPacket) }
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarBaguettes(uuid); return }

        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarBaguettes(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf<ItemDisplay>().apply {
                repeat(4) { add(crearBaguetteOrbitante(player.location)) }
            }
        }

        val anguloBase = angulos.getOrDefault(uuid, 0.0)
        val radio = 1.2

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / entidades.size) * i
                val x = radio * cos(anguloBase + offset)
                val z = radio * sin(anguloBase + offset)
                val y = 1.1 + (0.1 * sin((anguloBase + offset) * 2))

                val loc = player.location.clone().add(x, y, z)
                loc.yaw = ((anguloBase + offset) * 180 / Math.PI).toFloat() + 90f
                display.teleport(loc)
            }
        }
        angulos[uuid] = anguloBase + 0.12
    }

    private fun crearBaguetteOrbitante(loc: Location): ItemDisplay {
        return loc.world.spawn(loc, ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(Material.BREAD))
            id.transformation = Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf(), JomlVector3f(0.8f, 0.8f, 0.8f), Quaternionf())
            id.teleportDuration = 1
            id.interpolationDuration = 1
        }
    }

    // --- 🛠️ EQUIPAMIENTO (EL FIX) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // 🔥 FIX: Re-cargar si el caché está vacío (CraftEngine timing fix)
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) {
            preLoadKit()
        }

        // Equipar armadura
        itemKitCache["casco"]?.let { inv.helmet = it.clone() }
        itemKitCache["pechera"]?.let { inv.chestplate = it.clone() }
        itemKitCache["pantalones"]?.let { inv.leggings = it.clone() }
        itemKitCache["botas"]?.let { inv.boots = it.clone() }

        // Equipar hotbar
        itemKitCache["habilidad1"]?.let { inv.setItem(1, it.clone()) }
        itemKitCache["habilidad2"]?.let { inv.setItem(2, it.clone()) }
        itemKitCache["habilidad3"]?.let { inv.setItem(3, it.clone()) }
        itemKitCache["habilidad4"]?.let { inv.setItem(4, it.clone()) }
        itemKitCache["arma"]?.let { inv.setItem(8, it.clone()) }

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    private fun limpiarBaguettes(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiarBaguettes(it.uniqueId)
            it.removePotionEffect(PotionEffectType.DARKNESS)
        }
    }
}
