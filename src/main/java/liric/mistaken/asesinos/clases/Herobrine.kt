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
 * FIX: Error de inferencia en listas, PacketEvents 2.11.2 y Multi-Idioma.
 */
class Herobrine : Asesino(
    "herobrine",
    Mistaken.Companion.instance.messageConfig.getSpecificFile(null, "asesinos").getString("asesinos.herobrine.nombre", "Herobrine")!!
) {

    private val path = "asesinos.herobrine"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // Orbitadores
    private val blockOrbiters = ConcurrentHashMap<UUID, BlockDisplay>()
    private val itemOrbiters = ConcurrentHashMap<UUID, MutableList<Entity>>() // Cambiado a Entity para evitar error de tipos
    private val angulos = ConcurrentHashMap<UUID, Double>()

    private val markerEntities = ConcurrentHashMap.newKeySet<Entity>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinosConfig(null)
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("asesinos.herobrine.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("asesinos.herobrine.items.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        when (slot) {
            1 -> habilidadDashVacio(player)
            2 -> habilidadSaltoDimensional(player)
            3 -> habilidadEstrellaWither(player)
            4 -> habilidadErrorMundo(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 🛠️ EQUIPAMIENTO (SISTEMA MULTI-IDIOMA) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()
        val langAsesinos = plugin.messageConfig.getSpecificFile(player, "asesinos")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return
            val namePath = if (key == "arma") "asesinos.herobrine.items.arma_nombre" else "asesinos.herobrine.items.${key}_nombre"

            langAsesinos.getString(namePath)?.let {
                item.editMeta { m -> m.displayName(mm.deserialize(it)) }
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

        listOf("casco", "pechera", "pantalones", "botas").forEach { setLocalizedItem(0, it, true) }
        setLocalizedItem(0, "habilidad1")
        setLocalizedItem(1, "habilidad2")
        setLocalizedItem(2, "habilidad3")
        setLocalizedItem(3, "habilidad4")
        setLocalizedItem(8, "arma")

        player.inventory.heldItemSlot = 8
        player.updateInventory()
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
        player.teleport(gens.random().clone().add(0.5, 1.1, 0.5))
        player.playSound(player.location, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.8f)
    }

    private fun habilidadEstrellaWither(player: Player) {
        val skull = player.launchProjectile(WitherSkull::class.java)
        skull.yield = 0f
        scope.launch {
            var life = 0
            while (isActive && life < 60 && !skull.isDead) {
                withContext(plugin.bukkitDispatcher) {
                    val hit = skull.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull { !plugin.asesinoManager.esElAsesino(it) }
                    hit?.let {
                        plugin.combatManager.processTrueDamage(it, player, 5.0)
                        skull.remove(); cancel()
                    }
                }
                delay(50); life++
            }
        }
    }

    private fun habilidadErrorMundo(player: Player) {
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("HB_Team"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.DARK_PURPLE, WrapperPlayServerTeams.OptionData.NONE
        )

        Bukkit.getOnlinePlayers().forEach { online ->
            if (plugin.asesinoManager.esElAsesino(online)) return@forEach
            val createTeam = WrapperPlayServerTeams("hb_glow", WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, mutableListOf(online.name))
            PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)

            val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
            PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(online.entityId, metadata))
        }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                val removeTeam = WrapperPlayServerTeams("hb_glow", WrapperPlayServerTeams.TeamMode.REMOVE, null as ScoreBoardTeamInfo?, mutableListOf<String>())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        }, 200L)
    }

    // --- 🧊 MOTOR DE ÓRBITA (FIXED) ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }
        if (blockOrbiters[uuid]?.world != player.world) limpiarVisuales(uuid)

        // 1. Bloque de Netherrack
        val bMain = blockOrbiters.getOrPut(uuid) {
            player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                bd.setBlock(Material.NETHERRACK.createBlockData())
                bd.setTransformation(Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf()))
                bd.teleportDuration = 2; bd.interpolationDuration = 2
            }
        }

        // 2. 🔥 FIX: Estrella y Oro (Lista de tipo Entity)
        val extras = itemOrbiters.getOrPut(uuid) {
            val list = mutableListOf<Entity>()

            // Spawneamos Estrella
            val star = player.world.spawn(player.location, ItemDisplay::class.java) { display ->
                display.setItemStack(ItemStack(Material.NETHER_STAR))
                display.setTransformation(Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf()))
                display.teleportDuration = 2; display.interpolationDuration = 2
            }

            // Spawneamos Oro
            val gold = player.world.spawn(player.location, BlockDisplay::class.java) { display ->
                display.setBlock(Material.GOLD_BLOCK.createBlockData())
                display.setTransformation(Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf()))
                display.teleportDuration = 2; display.interpolationDuration = 2
            }

            list.add(star); list.add(gold)
            list
        }

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        val radio = 1.4

        bMain.teleport(player.location.clone().add(radio * cos(angulo), 1.2 + (0.2 * sin(angulo * 2)), radio * sin(angulo)))
        // Estrella (Offset 120°)
        extras[0].teleport(player.location.clone().add(radio * cos(angulo + 2.09), 1.0 + (0.2 * cos(angulo * 2)), radio * sin(angulo + 2.09)))
        // Oro (Offset 240°)
        extras[1].teleport(player.location.clone().add(radio * cos(angulo + 4.18), 0.8 + (0.2 * sin(angulo)), radio * sin(angulo + 4.18)))

        angulos[uuid] = angulo
    }

    override fun mostrarTrail(player: Player) {
        val l = player.location.add(0.0, 1.2, 0.0)
        val mgr = PacketEvents.getAPI().playerManager
        val pos = Vector3d(l.x, l.y, l.z)
        mgr.sendPacket(player.world.players, WrapperPlayServerParticle(Particle(ParticleTypes.CLOUD), false, pos, Vector3f(0.12f, 0.12f, 0.12f), 0.01f, 1))
    }

    private fun limpiarVisuales(uuid: UUID) {
        blockOrbiters.remove(uuid)?.remove()
        itemOrbiters.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarVisuales(it.uniqueId) }
        scope.coroutineContext.cancelChildren()
    }
}
