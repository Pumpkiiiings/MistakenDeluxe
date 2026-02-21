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
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.mainThread
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.attribute.Attribute
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
 * Optimización 1.21.4: Display Entities, PacketEvents 2.11.2 y Daño Real.
 */
class Herobrine : Asesino(
    "herobrine",
    Mistaken.Companion.instance.configManager.getAsesinos().getString("asesinos.herobrine.nombre", "<white><b>HEROBRINE</b>")!!
) {

    private val path = "asesinos.herobrine"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // --- 🧊 ORBITADORES ---
    private val blockOrbiters = ConcurrentHashMap<UUID, BlockDisplay>()
    private val itemOrbiters = ConcurrentHashMap<UUID, ItemDisplay>()
    private val angulos = ConcurrentHashMap<UUID, Double>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armorKeys.forEach { key ->
            config.getString("$path.armadura.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[key] = it }
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
        if (checkCooldown(player, slot)) return

        // Manteniendo tus slots originales (1, 2, 3, 4) como solicitaste
        when (slot) {
            1 -> habilidadDashVacio(player)
            2 -> habilidadSaltoDimensional(player)
            3 -> habilidadEstrellaWither(player)
            4 -> habilidadErrorMundo(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 🚀 TRAIL ASÍNCRONO ---
    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val l = player.location.add(0.0, 1.2, 0.0)
        val mgr = PacketEvents.getAPI().playerManager
        val pos = Vector3d(l.x, l.y, l.z)

        val cloud = WrapperPlayServerParticle(Particle(ParticleTypes.CLOUD), false, pos, Vector3f(0.12f, 0.12f, 0.12f), 0.01f, 1)

        l.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(l) < 625.0) {
                mgr.sendPacket(p, cloud)
                if (ThreadLocalRandom.current().nextFloat() < 0.2f) {
                    mgr.sendPacket(p, WrapperPlayServerParticle(Particle(ParticleTypes.SOUL_FIRE_FLAME), false, pos, Vector3f(0.1f, 0.1f, 0.1f), 0.01f, 1))
                }
            }
        }
    }

    // --- 🔄 ÓRBITA FÍSICA (Block & Item Display) ---
    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }
        if (blockOrbiters[uuid]?.world != player.world) limpiarVisuales(uuid)

        // 1. Bloque de Netherite
        val bDisplay = blockOrbiters.getOrPut(uuid) {
            player.world.spawn(player.location, BlockDisplay::class.java) {
                it.block = Material.NETHERITE_BLOCK.createBlockData()
                it.transformation = Transformation(JomlVector3f(-0.2f, -0.2f, -0.2f), Quaternionf(), JomlVector3f(0.4f, 0.4f, 0.4f), Quaternionf())
                it.teleportDuration = 2; it.interpolationDuration = 2
            }
        }

        // 2. Estrella del Nether
        val iDisplay = itemOrbiters.getOrPut(uuid) {
            player.world.spawn(player.location, ItemDisplay::class.java) {
                // FIX: Uso del método directo setItemStack
                it.setItemStack(ItemStack(Material.NETHER_STAR))
                it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
                it.teleportDuration = 2; it.interpolationDuration = 2
            }
        }

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        val radio = 1.4

        bDisplay.teleport(player.location.clone().add(radio * cos(angulo), 1.2 + (0.2 * sin(angulo * 2)), radio * sin(angulo)))
        iDisplay.teleport(player.location.clone().add(radio * cos(angulo + Math.PI), 1.0 + (0.2 * cos(angulo * 2)), radio * sin(angulo + Math.PI)))

        angulos[uuid] = angulo
    }

    // --- 🗡️ HABILIDADES ---

    private fun habilidadDashVacio(player: Player) {
        player.velocity = player.location.direction.multiply(2.4).setY(0.22)
        player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.5f)

        player.getNearbyEntities(3.0, 3.0, 3.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                plugin.combatManager.processTrueDamage(victim, player, 6.0)
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 4))
            }
        }
    }

    private fun habilidadSaltoDimensional(player: Player) {
        val gens = plugin.generatorManager.getGeneratorLocations()
        if (gens.isEmpty()) return
        val target = gens.random().clone().add(0.5, 1.1, 0.5)

        player.world.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, player.location.add(0.0, 1.0, 0.0), 15, 0.4, 0.6, 0.4, 0.1)
        player.teleport(target)
        player.playSound(player.location, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.8f)
    }

    private fun habilidadEstrellaWither(player: Player) {
        val skull = player.launchProjectile(WitherSkull::class.java)
        skull.yield = 0f

        scope.launch {
            var life = 0
            while (isActive && life < 60 && !skull.isDead) {
                withContext(plugin.mainThread) {
                    val hit = skull.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull { !plugin.asesinoManager.esElAsesino(it) }
                    hit?.let {
                        plugin.combatManager.processTrueDamage(it, player, 5.0)
                        skull.remove()
                        cancel()
                    }
                }
                delay(50); life++
            }
        }
    }

    private fun habilidadErrorMundo(player: Player) {
        // FIX: PacketEvents 2.11.2 TeamInfo
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("HB_Team"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
            WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.DARK_PURPLE,
            WrapperPlayServerTeams.OptionData.NONE
        )

        Bukkit.getOnlinePlayers().forEach { online ->
            if (plugin.asesinoManager.esElAsesino(online)) return@forEach

            // FIX: Uso de mutableListOf para evitar ambigüedad
            val createTeam = WrapperPlayServerTeams("hb_glow", WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, mutableListOf(online.name))
            PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)

            val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
            PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(online.entityId, metadata))
        }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                // FIX: Cast de null y mutableListOf para REMOVE
                val removeTeam = WrapperPlayServerTeams(
                    "hb_glow",
                    WrapperPlayServerTeams.TeamMode.REMOVE,
                    null as ScoreBoardTeamInfo?,
                    mutableListOf<String>()
                )
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        }, 200L)
    }

    // --- 🛠️ EQUIPAMIENTO (FIXED) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // FIX: Lazy load para asegurar carga tras CraftEngine
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        // Slots fijos solicitados
        inv.setItem(8, itemKitCache["arma"]?.clone())
        for (i in 1..4) {
            itemKitCache["habilidad$i"]?.let { inv.setItem(i - 1, it.clone()) }
        }

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    private fun limpiarVisuales(uuid: UUID) {
        blockOrbiters.remove(uuid)?.remove()
        itemOrbiters.remove(uuid)?.remove()
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarVisuales(it.uniqueId) }
        scope.coroutineContext.cancelChildren()
    }
}
