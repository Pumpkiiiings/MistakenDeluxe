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
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * Slasher: El Carnicero Implacable.
 * Optimización: Habilidades temporales con Coroutines y rastro de sangre con PacketEvents.
 */
class Slasher : Asesino(
    "slasher",
    Mistaken.instance.configManager.getAsesinos().getString("asesinos.slasher.nombre", "<red><b>SLASHER</b>")!!
) {

    private val pathBase = "asesinos.slasher"
    private val temporaryEntities = ConcurrentHashMap.newKeySet<Entity>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        reproducirEfectosHabilidad(player, slot)

        when (slot) {
            1 -> habilidadSedDeSangre(player)
            2 -> habilidadMacheteLanzable(player)
            3 -> habilidadPresencia(player)
            4 -> habilidadEjecucion(player)
        }
    }

    // --- 🚀 TRAIL DE SANGRE ASÍNCRONO (PacketEvents) ---

    override fun mostrarTrail(player: Player) {
        val l = player.location
        val mgr = PacketEvents.getAPI().playerManager
        val distSq = 625.0 // 25 bloques de culling

        // Partícula de polvo rojo (Sangre)
        val dustData = ParticleDustData(1.0f, 0.0f, 0.0f, 0.8f)
        val bloodPacket = WrapperPlayServerParticle(
            Particle(ParticleTypes.DUST, dustData),
            false, Vector3d(l.x, l.y + 2.0, l.z),
            Vector3f(0.1f, 0.1f, 0.1f), 0.02f, 1
        )

        Bukkit.getOnlinePlayers().forEach { p ->
            if (p != player && p.world == l.world && p.location.distanceSquared(l) < distSq) {
                mgr.sendPacket(p, bloodPacket)
            }
        }
    }

    // --- HABILIDADES ---

    private fun habilidadSedDeSangre(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 160, 2))
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 160, 1))

        // Dibujar estrella estética (Async math)
        dibujarEstrella(player, Color.RED, 1.5, 5)

        // Debuff post-frenesí con Coroutine
        scope.launch {
            delay(8000) // 160 ticks = 8 segundos
            withContext(Dispatchers.Main) {
                if (player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 1))
                    player.sendMessage(mm.deserialize("<red><i>Te sientes agotado por el frenesí...</i></red>"))
                }
            }
        }
    }

    private fun habilidadMacheteLanzable(player: Player) {
        val config = plugin.configManager.getAsesinos()
        val itemMachete = CraftEngineUtils.getCustomItemSafe(config.getString("$pathBase.items.arma"))

        val spawnLoc = player.eyeLocation.clone()
        val armorStand = player.world.spawn(spawnLoc, ArmorStand::class.java) { stand ->
            stand.isVisible = false
            stand.isMarker = true
            stand.isSmall = true
            stand.setGravity(false)
            stand.equipment.setItemInMainHand(itemMachete)
            stand.rightArmPose = EulerAngle(Math.toRadians(90.0), 0.0, 0.0)
        }

        temporaryEntities.add(armorStand)
        val direction = player.location.direction.multiply(1.5)

        val job = scope.launch {
            var ticks = 0
            while (isActive && ticks < 25 && !armorStand.isDead) {
                withContext(Dispatchers.Main) {
                    armorStand.teleport(armorStand.location.add(direction))
                    armorStand.rightArmPose = armorStand.rightArmPose.add(0.6, 0.0, 0.0)

                    // Trail visual
                    armorStand.world.spawnParticle(
                        org.bukkit.Particle.DUST, armorStand.location.add(0.0, 0.5, 0.0), 1,
                        org.bukkit.Particle.DustOptions(Color.YELLOW, 0.8f)
                    )

                    // Detección de impacto
                    val hit = armorStand.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }

                    hit?.let { victim ->
                        plugin.gameManager.combatManager.takeDamage(victim)
                        victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 40, 0))
                        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 1))

                        victim.world.spawnParticle(
                            org.bukkit.Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 8, 0.1, 0.1, 0.1,
                            Material.REDSTONE_BLOCK.createBlockData()
                        )
                        removeEntity(armorStand)
                        cancel()
                    }
                }
                delay(50) // 1 Tick
                ticks++
            }
            if (isActive) withContext(Dispatchers.Main) { removeEntity(armorStand) }
        }
        trackJob(job)
    }

    private fun habilidadPresencia(player: Player) {
        player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.8f)

        // Círculo de partículas optimizado
        val loc = player.location
        val dust = org.bukkit.Particle.DustOptions(Color.RED, 1.2f)
        var i = 0.0
        while (i < Math.PI * 2) {
            val x = cos(i) * 3.5
            val z = sin(i) * 3.5
            player.world.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(x, 1.0, z), 1, dust)
            i += Math.PI / 8
        }

        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 60, 1))
                victim.sendMessage(mm.deserialize("<obfuscated>Sientes su respiración...</obfuscated>"))
            }
        }
    }

    private fun habilidadEjecucion(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 300, 3))
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 300, 2))

        dibujarEstrella(player, Color.MAROON, 3.0, 5)

        scope.launch {
            delay(15000) // 300 ticks = 15 segundos
            withContext(Dispatchers.Main) {
                if (player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 2))
                }
            }
        }
    }

    private fun dibujarEstrella(player: Player, color: Color, radio: Double, puntas: Int) {
        val loc = player.location.add(0.0, 0.1, 0.0)
        val dust = org.bukkit.Particle.DustOptions(color, 1.0f)

        // Lógica de dibujo instantánea
        for (i in 0 until puntas) {
            val angle = i * Math.PI * 2 / puntas
            val nextAngle = (i + 2) * Math.PI * 2 / puntas

            val p1 = loc.clone().add(cos(angle) * radio, 0.0, sin(angle) * radio)
            val p2 = loc.clone().add(cos(nextAngle) * radio, 0.0, sin(nextAngle) * radio)

            val dir = p2.toVector().subtract(p1.toVector())
            val dist = dir.length()
            dir.normalize()

            var d = 0.0
            while (d < dist) {
                player.world.spawnParticle(org.bukkit.Particle.DUST, p1.clone().add(dir.clone().multiply(d)), 1, dust)
                d += 0.4
            }
        }
    }

    private fun removeEntity(entity: Entity) {
        entity.remove()
        temporaryEntities.remove(entity)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        temporaryEntities.forEach { it.remove() }
        temporaryEntities.clear()
        scope.coroutineContext.cancelChildren()
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        val config = plugin.configManager.getAsesinos()

        // Armadura (Safe parsing)
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

    override fun mostrarTrailFisico(player: Player) {}
}
