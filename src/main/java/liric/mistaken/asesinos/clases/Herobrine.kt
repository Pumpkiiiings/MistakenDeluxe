package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
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
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.MagmaCube
import org.bukkit.entity.Player
import org.bukkit.entity.WitherSkull
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0]
 * Herobrine: El Rey del Vacío.
 * Optimización extrema: Coroutines para tareas de tiempo y PacketEvents para visuales.
 */
class Herobrine : Asesino(
    "herobrine",
    Mistaken.instance.configManager.getAsesinos().getString("asesinos.herobrine.nombre", "<white><b>HEROBRINE</b>")!!
) {

    private val path = "asesinos.herobrine"
    private val markerEntities = ConcurrentHashMap.newKeySet<org.bukkit.entity.Entity>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        reproducirEfectosHabilidad(player, slot)

        when (slot) {
            1 -> habilidadDashVacio(player)
            2 -> habilidadSaltoDimensional(player)
            3 -> habilidadEstrellaWither(player)
            4 -> habilidadErrorMundo(player)
        }
    }

    // --- 🚀 TRAIL ASÍNCRONO (PacketEvents) ---

    override fun mostrarTrail(player: Player) {
        val l = player.location.add(0.0, 1.2, 0.0)
        val pos = Vector3d(l.x, l.y, l.z)
        val off = Vector3f(0.12f, 0.12f, 0.12f)
        val mgr = PacketEvents.getAPI().playerManager

        val cloudPacket = WrapperPlayServerParticle(Particle(ParticleTypes.CLOUD), false, pos, off, 0.01f, 1)
        val distSq = 625.0 // 25 bloques

        Bukkit.getOnlinePlayers().forEach { p ->
            if (p != player && p.world == l.world && p.location.distanceSquared(l) < distSq) {
                mgr.sendPacket(p, cloudPacket)

                if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                    val soulPacket = WrapperPlayServerParticle(Particle(ParticleTypes.SOUL_FIRE_FLAME), false, pos, off, 0.01f, 1)
                    mgr.sendPacket(p, soulPacket)
                }
            }
        }
    }

    // --- HABILIDADES ---

    private fun habilidadDashVacio(player: Player) {
        val origin = player.location.clone()
        val dir = player.location.direction

        player.world.spawnParticle(org.bukkit.Particle.FLASH, origin.add(0.0, 1.0, 0.0), 2, 0.1, 0.1, 0.1, 0.0)
        player.world.spawnParticle(org.bukkit.Particle.FLAME, origin, 10, 0.2, 0.2, 0.2, 0.05)

        player.velocity = dir.multiply(2.4).setY(0.22)
        player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.5f)
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))

        player.getNearbyEntities(3.0, 3.0, 3.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                plugin.gameManager.combatManager.takeDamage(victim)
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 4))
                victim.playSound(victim.location, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 1.2f)
            }
        }
    }

    private fun habilidadSaltoDimensional(player: Player) {
        val gens = plugin.generatorManager.getGeneratorLocations()
        if (gens.isEmpty()) return

        val target = gens.random().clone().add(0.5, 1.1, 0.5)

        player.world.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, player.location.add(0.0, 1.0, 0.0), 15, 0.4, 0.6, 0.4, 0.1)
        player.playSound(player.location, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 0.5f)

        player.teleport(target)

        player.world.spawnParticle(org.bukkit.Particle.CLOUD, player.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.05)
        player.playSound(player.location, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.8f)
    }

    private fun habilidadEstrellaWither(player: Player) {
        val skull = player.launchProjectile(WitherSkull::class.java)
        skull.isCharged = false
        skull.yield = 0f

        val job = scope.launch {
            var life = 0
            while (isActive && life < 60 && !skull.isDead && skull.isValid) {
                withContext(Dispatchers.Main) {
                    skull.world.spawnParticle(org.bukkit.Particle.END_ROD, skull.location, 1, 0.0, 0.0, 0.0, 0.01)

                    val hit = skull.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }

                    hit?.let { victim ->
                        plugin.gameManager.combatManager.takeDamage(victim)
                        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
                        victim.playSound(victim.location, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.5f)
                        skull.remove()
                        cancel()
                    }
                }
                delay(50)
                life++
            }
        }
        trackJob(job)
    }

    private fun habilidadErrorMundo(player: Player) {
        val board = Bukkit.getScoreboardManager().mainScoreboard
        val team = board.getTeam("hb_purple") ?: board.registerNewTeam("hb_purple")
        team.color(NamedTextColor.DARK_PURPLE)

        val mgr = PacketEvents.getAPI().playerManager

        Bukkit.getOnlinePlayers().forEach { online ->
            if (plugin.asesinoManager.esElAsesino(online)) return@forEach

            team.addEntry(online.name)

            // Paquete de Glowing manual (Bitmask 0x40)
            val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
            mgr.sendPacket(player, WrapperPlayServerEntityMetadata(online.entityId, metadata))

            online.playSound(online.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 2.0f)

            // Tarea de reset para este jugador
            scope.launch {
                delay(10000) // 10 segundos (200 ticks)
                withContext(Dispatchers.Main) {
                    if (online.isOnline) {
                        team.removeEntry(online.name)
                        val resetMeta = listOf(EntityData(0, EntityDataTypes.BYTE, 0x00.toByte()))
                        mgr.sendPacket(player, WrapperPlayServerEntityMetadata(online.entityId, resetMeta))
                    }
                }
            }
        }

        // Spawneo de entidades marcador en generadores
        plugin.generatorManager.getGeneratorLocations().forEach { loc ->
            if (!plugin.generatorManager.isCompleted(loc)) {
                loc.world?.spawn(loc.clone().add(0.5, 0.1, 0.5), MagmaCube::class.java) { cube ->
                    cube.size = 1
                    cube.isInvisible = true
                    cube.setAI(false)
                    cube.isGlowing = true
                    cube.isInvulnerable = true
                    team.addEntry(cube.uniqueId.toString())
                    markerEntities.add(cube)
                }?.let { spawned ->
                    scope.launch {
                        delay(10000)
                        withContext(Dispatchers.Main) {
                            spawned.remove()
                            markerEntities.remove(spawned)
                        }
                    }
                }
            }
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        markerEntities.forEach { it.remove() }
        markerEntities.clear()
        scope.coroutineContext.cancelChildren()
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        val config = plugin.configManager.getAsesinos()

        inv.helmet = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.casco"))
        inv.chestplate = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.pechera"))
        inv.leggings = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.pantalones"))
        inv.boots = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.botas"))

        for (i in 0..4) {
            val key = if (i == 0) "arma" else "habilidad$i"
            val id = config.getString("$path.items.$key")
            val name = config.getString("$path.items.${key}_nombre")

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
