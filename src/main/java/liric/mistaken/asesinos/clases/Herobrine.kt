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
 * FIX: Soporte Multi-Idioma, Fallback Vanilla y PacketEvents nativo.
 */
class Herobrine : Asesino(
    "herobrine",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.herobrine.nombre", "<white><b>HEROBRINE</b>", "asesinos_info")
) {

    private val path = "asesinos.herobrine"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // Orbitadores
    private val blockOrbiters = ConcurrentHashMap<UUID, BlockDisplay>()
    private val itemOrbiters = ConcurrentHashMap<UUID, MutableList<Entity>>() // Múltiples tipos
    private val angulos = ConcurrentHashMap<UUID, Double>()

    private val markerEntities = ConcurrentHashMap.newKeySet<Entity>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos() // Archivo global de mecánicas
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$path.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("$path.items.$k")?.let { id ->
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
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            // Ruta del asesinos_info.yml
            val namePath = if (key == "arma") "asesinos.herobrine.habilidades_nombres.arma"
            else "asesinos.herobrine.habilidades_nombres.$key"

            langInfo.getString(namePath)?.let {
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
        setLocalizedItem(1, "habilidad1")
        setLocalizedItem(2, "habilidad2")
        setLocalizedItem(3, "habilidad3")
        setLocalizedItem(4, "habilidad4")
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
                plugin.gameManager.combatManager.takeDamage(victim)
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

        val job = scope.launch {
            var life = 0
            while (isActive && life < 60 && !skull.isDead) {
                withContext(plugin.bukkitDispatcher) {
                    val hit = skull.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }
                    hit?.let {
                        plugin.gameManager.combatManager.takeDamage(it)
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
        val teamName = "hb_glow"

        // 1. Configuramos el Info del equipo
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("HB_Team"),
            Component.empty(),
            Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
            WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.DARK_PURPLE,
            WrapperPlayServerTeams.OptionData.NONE
        )

        // 2. Aplicamos a cada jugador
        Bukkit.getOnlinePlayers().forEach { online ->
            if (plugin.asesinoManager.esElAsesino(online)) return@forEach

            // Paquete 1: Meterlo al equipo (Color morado)
            val createTeam = WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.CREATE,
                teamInfo,
                listOf(online.name) // En Kotlin, pasamos List en vez de MutableList si la librería lo permite
            )
            PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)

            // Paquete 2: Encender el Glowing (Bitmask)
            val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
            PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(online.entityId, metadata))
        }

        // 3. Tarea asíncrona para limpiar el desmadre después de 10 segundos
        scope.launch {
            delay(10000) // 10s
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline) {

                    // 🔥 EL FIX ESTÁ AQUÍ 🔥
                    // En PacketEvents, para borrar un equipo solo necesitas el nombre y el modo REMOVE.
                    // Opcionalmente pasas una lista vacía de jugadores, dependiendo de la firma.
                    val removeTeam = WrapperPlayServerTeams(
                        teamName,
                        WrapperPlayServerTeams.TeamMode.REMOVE,
                        Optional.empty<ScoreBoardTeamInfo>() // Evitamos el 'null' directo que marea a Kotlin
                    )

                    PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
                }
            }
        }
    }

    // --- 🧊 MOTOR DE ÓRBITA ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }
        if (blockOrbiters[uuid]?.world != player.world) limpiarVisuales(uuid)

        // 1. Bloque de Netherrack
        val bMain = blockOrbiters.getOrPut(uuid) {
            player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                bd.block = Material.NETHERRACK.createBlockData()
                bd.transformation = Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf())
                bd.teleportDuration = 2; bd.interpolationDuration = 2
            }
        }

        // 2. Estrella y Oro
        val extras = itemOrbiters.getOrPut(uuid) {
            val list = mutableListOf<Entity>()
            val star = player.world.spawn(player.location, ItemDisplay::class.java) { display ->
                display.setItemStack(ItemStack(Material.NETHER_STAR))
                display.transformation = Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
                display.teleportDuration = 2; display.interpolationDuration = 2
            }
            val gold = player.world.spawn(player.location, BlockDisplay::class.java) { display ->
                display.block = Material.GOLD_BLOCK.createBlockData()
                display.transformation = Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf())
                display.teleportDuration = 2; display.interpolationDuration = 2
            }
            list.add(star); list.add(gold)
            list
        }

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        val radio = 1.4

        bMain.teleport(player.location.clone().add(radio * cos(angulo), 1.2 + (0.2 * sin(angulo * 2)), radio * sin(angulo)))
        extras[0].teleport(player.location.clone().add(radio * cos(angulo + 2.09), 1.0 + (0.2 * cos(angulo * 2)), radio * sin(angulo + 2.09)))
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
