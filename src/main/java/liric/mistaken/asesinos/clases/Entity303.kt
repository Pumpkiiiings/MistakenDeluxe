package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.Particle
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

/**
 * [LIRIC-MISTAKEN 2.0]
 * Entity303: El Asesino Hacker.
 * Optimización: Lógica de proyectiles y timers mediante Coroutines.
 */
class Entity303 : Asesino(
    "entity303",
    Mistaken.instance.configManager.getAsesinos().getString("asesinos.entity303.nombre", "<red><b>ENTITY 303</b>")!!
) {

    private val pathBase = "asesinos.entity303"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        reproducirEfectosHabilidad(player, slot)

        when (slot) {
            1 -> habilidadDashCodigo(player)
            2 -> habilidadInfeccionSistema(player)
            3 -> habilidadProtocoloVuelo(player)
            4 -> habilidadCrashPantalla(player)
        }
    }

    // --- 🚀 TRAIL ASÍNCRONO (PacketEvents) ---

    override fun mostrarTrail(player: Player) {
        val l = player.location
        // Polvo rojo hacker (R: 1, G: 0, B: 0, Tamaño: 0.8)
        val dustData = ParticleDustData(1.0f, 0.0f, 0.0f, 0.8f)
        val particle = Particle(ParticleTypes.DUST, dustData)

        val packet = WrapperPlayServerParticle(
            particle, false,
            Vector3d(l.x, l.y + 1.1, l.z),
            Vector3f(0.15f, 0.15f, 0.15f), 0.0f, 1
        )

        // Culling de 25 bloques (O(1) distance check)
        val distSq = 625.0
        Bukkit.getOnlinePlayers().forEach { p ->
            if (p != player && p.world == l.world && p.location.distanceSquared(l) < distSq) {
                PacketEvents.getAPI().playerManager.sendPacket(p, packet)
            }
        }
    }

    // --- HABILIDADES CON COROUTINES ---

    private fun habilidadDashCodigo(player: Player) {
        val dir = player.location.direction
        player.velocity = dir.multiply(2.5).setY(0.25)
        player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 1f, 1.5f)
        player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 2f)

        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))

        val job = scope.launch {
            var ticks = 0
            while (isActive && ticks < 12 && player.isOnline) {
                withContext(Dispatchers.Main) {
                    val loc = player.location
                    loc.world.spawnParticle(org.bukkit.Particle.FLAME, loc, 5, 0.1, 0.1, 0.1, 0.05)
                    loc.world.spawnParticle(org.bukkit.Particle.ENCHANT, loc, 10, 0.2, 0.2, 0.2, 0.1)

                    // Detección de víctimas
                    val victims = player.getNearbyEntities(1.5, 1.5, 1.5).filterIsInstance<Player>()
                    for (victim in victims) {
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            plugin.gameManager.combatManager.takeDamage(victim)
                            victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                            cancel() // Romper loop si golpea
                            return@withContext
                        }
                    }
                }
                delay(50L) // 1 Tick
                ticks++
            }
        }
        trackJob(job)
    }

    private fun habilidadInfeccionSistema(player: Player) {
        val infector = player.launchProjectile(Snowball::class.java)
        infector.customName(mm.deserialize("303_infection"))
        CraftEngineUtils.getCustomItem("NETHER_STAR")?.let { infector.item = it }

        val job = scope.launch {
            while (isActive && !infector.isDead && infector.isValid) {
                withContext(Dispatchers.Main) {
                    val loc = infector.location
                    loc.world.spawnParticle(org.bukkit.Particle.END_ROD, loc, 2, 0.02, 0.02, 0.02, 0.01)
                    loc.world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, loc, 2, 0.02, 0.02, 0.02, 0.01)

                    // Búsqueda de impacto manual para mayor precisión
                    val hit = infector.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }

                    hit?.let { victim ->
                        plugin.gameManager.combatManager.takeDamage(victim)
                        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
                        victim.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 60, 1))
                        victim.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.8f)
                        infector.remove()
                        cancel()
                    }
                }
                delay(50L)
            }
        }
        trackJob(job)
    }

    private fun habilidadProtocoloVuelo(player: Player) {
        player.allowFlight = true
        player.isFlying = true
        player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)

        val job = scope.launch {
            var ticks = 0
            while (isActive && ticks < 80 && player.isOnline && player.isFlying) {
                if (!plugin.asesinoManager.esElAsesino(player)) break

                withContext(Dispatchers.Main) {
                    player.world.spawnParticle(org.bukkit.Particle.SMALL_FLAME, player.location, 2, 0.2, 0.0, 0.2, 0.02)
                }
                delay(250L) // Cada 5 ticks
                ticks += 5
            }
            // Al terminar o cancelar
            withContext(Dispatchers.Main) {
                player.isFlying = false
                player.allowFlight = false
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
                player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 2f)
            }
        }
        trackJob(job)
    }

    private fun habilidadCrashPantalla(player: Player) {
        Bukkit.getOnlinePlayers().forEach { online ->
            if (!plugin.asesinoManager.esElAsesino(online)) {
                online.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 120, 0))
                online.playSound(online.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
            }
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            it.allowFlight = false
            it.isFlying = false
        }
        scope.coroutineContext.cancelChildren()
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        val config = plugin.configManager.getAsesinos()

        // Armadura
        inv.helmet = CraftEngineUtils.getCustomItem(config.getString("$pathBase.armadura.casco"))
        inv.chestplate = CraftEngineUtils.getCustomItem(config.getString("$pathBase.armadura.pechera"))
        inv.leggings = CraftEngineUtils.getCustomItem(config.getString("$pathBase.armadura.pantalones"))
        inv.boots = CraftEngineUtils.getCustomItem(config.getString("$pathBase.armadura.botas"))

        // Items de habilidad
        for (i in 0..4) {
            val itemKey = if (i == 0) "arma" else "habilidad$i"
            val id = config.getString("$pathBase.items.$itemKey")
            val name = config.getString("$pathBase.items.${itemKey}_nombre")

            if (!id.isNullOrBlank() && !id.equals("none", true)) {
                val item = CraftEngineUtils.getCustomItem(id)
                item?.let {
                    if (name != null) it.editMeta { m -> m.displayName(mm.deserialize(name)) }
                    inv.setItem(if (i == 0) 8 else i, it)
                }
            }
        }
        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }
}
