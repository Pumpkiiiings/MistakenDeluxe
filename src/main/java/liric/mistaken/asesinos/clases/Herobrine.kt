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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * Herobrine: El Rey del Vacío.
 * Corregido: PacketEvents 2.11.2, Paper 1.21.4 y Dispatchers.
 */
class Herobrine : Asesino(
    "herobrine",
    Mistaken.instance.configManager.getAsesinos().getString("asesinos.herobrine.nombre", "<white><b>HEROBRINE</b>")!!
) {

    private val path = "asesinos.herobrine"
    private val markerEntities = ConcurrentHashMap.newKeySet<org.bukkit.entity.Entity>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // --- 🧊 VARIABLES PARA ÓRBITA MÍSTICA ---
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<Entity>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val orbitMaterials = listOf(Material.NETHERRACK, Material.NETHER_STAR, Material.GOLD_BLOCK)

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

        l.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(l) < distSq) {
                mgr.sendPacket(p, cloudPacket)

                if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                    val soulPacket = WrapperPlayServerParticle(Particle(ParticleTypes.SOUL_FIRE_FLAME), false, pos, off, 0.01f, 1)
                    mgr.sendPacket(p, soulPacket)
                }
            }
        }
    }

    // --- 🧊 MOTOR DE ÓRBITA (NATIVO 1.21.4) ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarOrbitadores(uuid); return }

        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarOrbitadores(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf<Entity>().apply {
                orbitMaterials.forEach { mat ->
                    if (mat == Material.NETHER_STAR) {
                        add(player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                            id.setItemStack(ItemStack(mat))
                            id.setTransformation(Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf()))
                            id.teleportDuration = 2
                            id.interpolationDuration = 2
                        })
                    } else {
                        add(player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                            bd.block = mat.createBlockData()
                            bd.setTransformation(Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf()))
                            bd.teleportDuration = 2
                            bd.interpolationDuration = 2
                        })
                    }
                }
            }
        }

        val anguloBase = angulos.getOrDefault(uuid, 0.0)
        val radio = 1.3
        val size = entidades.size

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / size) * i
                val x = radio * cos(anguloBase + offset)
                val z = radio * sin(anguloBase + offset)
                val y = 1.1 + (0.2 * sin((anguloBase + offset) * 2))

                display.teleport(player.location.clone().add(x, y, z))
            }
        }
        angulos[uuid] = (anguloBase + 0.12) % (Math.PI * 2)
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
                plugin.combatManager.processTrueDamage(victim, player, 6.0)
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 4))
                victim.playSound(victim.location, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 1.2f)
            }
        }
    }

    private fun habilidadSaltoDimensional(player: Player) {
        val gens = plugin.generatorManager.getGeneratorLocations()
        if (gens.isEmpty()) return
        player.teleport(gens.random().clone().add(0.5, 1.1, 0.5))
        player.playSound(player.location, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.8f)
    }

    private fun habilidadEstrellaWither(player: Player) {
        val skull = player.launchProjectile(WitherSkull::class.java)
        skull.yield = 0f

        val job = scope.launch {
            var life = 0
            while (isActive && life < 60 && !skull.isDead && skull.isValid) {
                // 🔥 FIX: Uso del dispatcher de Paper
                withContext(plugin.bukkitDispatcher) {
                    val hit = skull.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }

                    hit?.let { victim ->
                        plugin.combatManager.processTrueDamage(victim, player, 5.0)
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
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("HB_Team"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.DARK_PURPLE, WrapperPlayServerTeams.OptionData.NONE
        )

        Bukkit.getOnlinePlayers().forEach { online ->
            if (plugin.asesinoManager.esElAsesino(online)) return@forEach

            // 🔥 FIX: Ambigüedad de constructor solucionada con mutableListOf
            val createTeam = WrapperPlayServerTeams("hb_glow", WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, mutableListOf(online.name))
            PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)

            val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
            PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(online.entityId, metadata))

            online.playSound(online.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 2.0f)

            scope.launch {
                delay(10000)
                // 🔥 FIX: Uso del dispatcher de Paper
                withContext(plugin.bukkitDispatcher) {
                    if (online.isOnline) {
                        val resetMeta = listOf(EntityData(0, EntityDataTypes.BYTE, 0x00.toByte()))
                        PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(online.entityId, resetMeta))

                        val removeTeam = WrapperPlayServerTeams("hb_glow", WrapperPlayServerTeams.TeamMode.REMOVE, null as ScoreBoardTeamInfo?, mutableListOf<String>())
                        PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
                    }
                }
            }
        }

        plugin.generatorManager.getGeneratorLocations().forEach { loc ->
            if (!plugin.generatorManager.isCompleted(loc)) {
                loc.world?.spawn(loc.clone().add(0.5, 0.1, 0.5), MagmaCube::class.java) { cube ->
                    cube.size = 1
                    cube.isInvisible = true
                    cube.setAI(false)
                    cube.isGlowing = true
                    cube.isInvulnerable = true
                    markerEntities.add(cube)
                }?.let { spawned ->
                    scope.launch {
                        delay(10000)
                        withContext(plugin.bukkitDispatcher) {
                            spawned.remove()
                            markerEntities.remove(spawned)
                        }
                    }
                }
            }
        }
    }

    private fun limpiarOrbitadores(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        markerEntities.forEach { it.remove() }
        markerEntities.clear()
        player?.let { limpiarOrbitadores(it.uniqueId) }
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

        for (i in 1..4) {
            val id = config.getString("$path.items.habilidad$i")
            val name = config.getString("$path.items.habilidad${i}_nombre")

            if (!id.isNullOrBlank() && !id.equals("none", true)) {
                CraftEngineUtils.getCustomItem(id)?.let { item ->
                    name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                    inv.setItem(i - 1, item)
                }
            }
        }

        val armaId = config.getString("$path.items.arma")
        val armaName = config.getString("$path.items.arma_nombre")
        CraftEngineUtils.getCustomItem(armaId)?.let { item ->
            armaName?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
            inv.setItem(8, item)
        }

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }
}
