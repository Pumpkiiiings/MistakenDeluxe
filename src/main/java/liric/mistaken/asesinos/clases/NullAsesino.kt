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
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EvokerFangs
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * NullAsesino: El Ente del Glitch.
 * Optimización: Trampas y efectos manejados por Coroutines y visuales por Paquetes.
 */
class NullAsesino : Asesino(
    "null",
    Mistaken.instance.configManager.getAsesinos().getString("asesinos.null.nombre", "<dark_gray><b>NULL</b>")!!
) {

    private val pathBase = "asesinos.null"
    private val activeTraps = ConcurrentHashMap.newKeySet<Entity>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        reproducirEfectosHabilidad(player, slot)

        when (slot) {
            1 -> habilidadErrorRender(player)
            2 -> habilidadGeneradorBait(player)
            3 -> habilidadPrisionVacio(player)
            4 -> habilidadColmillosVacio(player)
        }
    }

    // --- 🚀 TRAIL GLITCH (ASÍNCRONO CON PACKETS) ---

    override fun mostrarTrail(player: Player) {
        val l = player.location.add(0.0, 1.1, 0.0)
        val pos = Vector3d(l.x, l.y, l.z)
        val off = Vector3f(0.15f, 0.2f, 0.15f)
        val mgr = PacketEvents.getAPI().playerManager

        // Partículas de End Rod y Bruja (Glitch morado/blanco)
        val white = WrapperPlayServerParticle(Particle(ParticleTypes.END_ROD), false, pos, off, 0.02f, 1)
        val purple = WrapperPlayServerParticle(Particle(ParticleTypes.WITCH), false, pos, off, 0.02f, 1)

        val distSq = 625.0 // 25 bloques de culling
        Bukkit.getOnlinePlayers().forEach { p ->
            if (p != player && p.world == l.world && p.location.distanceSquared(l) < distSq) {
                mgr.sendPacket(p, white)
                mgr.sendPacket(p, purple)

                if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                    val smoke = WrapperPlayServerParticle(Particle(ParticleTypes.SMOKE), false, pos, off, 0.01f, 1)
                    mgr.sendPacket(p, smoke)
                }
            }
        }
    }

    // --- HABILIDADES ---

    private fun habilidadErrorRender(player: Player) {
        player.world.playSound(player.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
        player.world.playSound(player.location, Sound.ENTITY_WARDEN_DIG, 1f, 1.5f)
        player.world.spawnParticle(org.bukkit.Particle.FLASH, player.location.add(0.0, 1.0, 0.0), 3, 0.5, 0.5, 0.5, 0.0)

        player.getNearbyEntities(12.0, 12.0, 12.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.apply {
                    addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 200, 0))
                    addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
                    addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 0))
                    sendMessage(mm.deserialize("<dark_gray><obfuscated>ERR</obfuscated> <white><b>SISTEMA CORRUPTO</b> <dark_gray><obfuscated>ERR</obfuscated>"))
                    playSound(location, Sound.BLOCK_CONDUIT_DEACTIVATE, 1f, 0.1f)
                }
            }
        }
    }

    private fun habilidadGeneradorBait(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.1, 0.5)

        // Spawneo de ArmorStand (Main Thread)
        val bait = loc.world?.spawn(loc, ArmorStand::class.java) { asEntity ->
            asEntity.isVisible = false
            asEntity.setGravity(false)
            asEntity.isMarker = true
            asEntity.equipment.helmet = org.bukkit.inventory.ItemStack(Material.BEACON)
        } ?: return

        activeTraps.add(bait)

        // Loop de la trampa con Coroutine
        val job = scope.launch {
            var timer = 0
            while (isActive && timer < 400 && !bait.isDead) {
                withContext(Dispatchers.Main) {
                    val angle = timer * 0.4
                    val x = cos(angle) * 0.7
                    val z = sin(angle) * 0.7

                    loc.world?.spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(x, 1.0, z), 1, 0.0, 0.0, 0.0, 0.0)
                    loc.world?.spawnParticle(org.bukkit.Particle.WITCH, loc.clone().add(-x, 1.0, -z), 1, 0.0, 0.0, 0.0, 0.0)

                    // Detección de víctimas
                    val hit = loc.world?.getNearbyEntities(loc, 3.5, 3.5, 3.5)?.filterIsInstance<Player>()?.firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }

                    hit?.let { victim ->
                        activarTrampa(victim, bait)
                        cancel() // Detener corrutina
                    }
                }
                delay(100) // 2 ticks
                timer++
            }
            if (isActive) withContext(Dispatchers.Main) { cleanupTrap(bait) }
        }
        trackJob(job)
    }

    private fun activarTrampa(victim: Player, bait: ArmorStand) {
        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 300, 0))
        victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.1f)
        victim.world.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, victim.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.05)
        cleanupTrap(bait)
    }

    private fun habilidadPrisionVacio(player: Player) {
        val ray = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, 15.0) {
            it is Player && !plugin.asesinoManager.esElAsesino(it)
        }

        val victim = ray?.hitEntity as? Player ?: return

        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 10))
        victim.playSound(victim.location, Sound.BLOCK_CHAIN_PLACE, 1f, 0.5f)
        victim.playSound(victim.location, Sound.ENTITY_WITHER_SPAWN, 0.3f, 2f)

        // Efecto visual temporal
        val job = scope.launch {
            var t = 0
            while (isActive && t < 15 && victim.isOnline) {
                withContext(Dispatchers.Main) {
                    victim.world.spawnParticle(org.bukkit.Particle.END_ROD, victim.location.add(0.0, 1.0, 0.0), 8, 0.4, 0.6, 0.4, 0.05)
                }
                delay(200) // 4 ticks
                t++
            }
        }
        trackJob(job)
    }

    private fun habilidadColmillosVacio(player: Player) {
        val startLoc = player.location
        val direction = startLoc.direction.setY(0.0).normalize()

        val job = scope.launch {
            val currentLoc = startLoc.clone()
            repeat(15) {
                if (!player.isOnline) return@launch

                withContext(Dispatchers.Main) {
                    currentLoc.add(direction)
                    currentLoc.world?.spawnParticle(org.bukkit.Particle.WITCH, currentLoc, 4, 0.2, 0.1, 0.2, 0.02)
                    currentLoc.world?.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, currentLoc, 2, 0.1, 0.1, 0.1, 0.01)
                    currentLoc.world?.playSound(currentLoc, Sound.BLOCK_NYLIUM_BREAK, 0.8f, 0.1f)
                    currentLoc.world?.spawn(currentLoc, EvokerFangs::class.java)
                }
                delay(50) // 1 tick
            }
        }
        trackJob(job)
    }

    private fun cleanupTrap(trap: Entity) {
        trap.remove()
        activeTraps.remove(trap)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        activeTraps.forEach { it.remove() }
        activeTraps.clear()
        scope.coroutineContext.cancelChildren()
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        val config = plugin.configManager.getAsesinos()

        inv.helmet = CraftEngineUtils.getCustomItem(config.getString("$pathBase.armadura.casco"))
        inv.chestplate = CraftEngineUtils.getCustomItem(config.getString("$pathBase.armadura.pechera"))
        inv.leggings = CraftEngineUtils.getCustomItem(config.getString("$pathBase.armadura.pantalones"))
        inv.boots = CraftEngineUtils.getCustomItem(config.getString("$pathBase.armadura.botas"))

        for (i in 0..4) {
            val key = if (i == 0) "arma" else "habilidad$i"
            val id = config.getString("$pathBase.items.$key")
            val name = config.getString("$pathBase.items.${key}_nombre")

            if (!id.isNullOrBlank() && !id.equals("none", true)) {
                CraftEngineUtils.getCustomItem(id)?.let { item ->
                    name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                    inv.setItem(if (i == 0) 8 else i, item)
                }
            }
        }
        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }
}
