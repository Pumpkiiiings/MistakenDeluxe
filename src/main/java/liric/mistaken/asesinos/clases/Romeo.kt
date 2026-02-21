package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
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
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * Romeo: El Administrador del Mundo (MSM Edition)
 * H1: Admin Dash (Colisión) | H2: Visión Admin | H3: Colmillos Triple | H4: Nether Star
 */
class Romeo : Asesino(
    "romeo",
    Mistaken.instance.configManager.getAsesinos().getString("asesinos.romeo.nombre", "<gradient:#ff0000:#ffff00><b>ROMEO</b></gradient>")!!
) {

    private val path = "asesinos.romeo"
    private val orbitadores = ConcurrentHashMap<UUID, BlockDisplay>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$path.armadura.$k")?.let { id ->
                CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[k] = it }
            }
        }

        items.forEach { k ->
            config.getString("$path.items.$k")?.let { id ->
                val name = config.getString("$path.items.${k}_nombre")
                CraftEngineUtils.getCustomItem(id)?.let { item ->
                    name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        when (slot) {
            1 -> habilidadAdminDash(player)
            2 -> habilidadAdminVision(player)
            3 -> habilidadTripleColmillo(player)
            4 -> habilidadNetherStar(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 🏃 H1: ADMIN DASH (5 SEGUNDOS) ---
    private fun habilidadAdminDash(player: Player) {
        val job = scope.launch {
            var duration = 100 // 5 segundos
            withContext(plugin.mainThread) {
                while (duration > 0 && player.isOnline) {
                    val dir = player.location.direction.normalize().multiply(1.4)
                    player.velocity = dir

                    // 1. Chocar con pared (Quita 3 corazones = 6 HP)
                    val frontBlock = player.location.add(dir.clone().multiply(0.5)).block
                    if (frontBlock.type.isSolid) {
                        plugin.combatManager.processTrueDamage(player, null, 6.0)
                        player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                        player.sendMessage(mm.deserialize("<red><b>[!]</b> ¡Impacto brutal contra el entorno!</red>"))
                        break
                    }

                    // 2. Chocar con Jugador (Quita 2.5 corazones = 5 HP)
                    player.getNearbyEntities(1.5, 1.5, 1.5).filterIsInstance<Player>().firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }?.let { victim ->
                        plugin.combatManager.processTrueDamage(victim, player, 5.0)
                        player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f)
                        duration = 0
                    }

                    delay(50)
                    duration--
                }
            }
        }
        trackJob(job)
    }

    // --- 👁️ H2: ADMIN VISION (GLOW BLANCO VIRTUAL) ---
    private fun habilidadAdminVision(player: Player) {
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("AdminTeam"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.NONE
        )

        player.getNearbyEntities(100.0, 100.0, 100.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                // Creamos equipo virtual blanco solo para Romeo
                val createTeam = WrapperPlayServerTeams("admin_glow", WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, mutableListOf(victim.name))
                PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)

                // Aplicamos el brillo
                val packet = WrapperPlayServerEntityMetadata(victim.entityId, listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte())))
                PacketEvents.getAPI().playerManager.sendPacket(player, packet)
            }
        }

        // Limpiar en 10 segundos
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                val removeTeam = WrapperPlayServerTeams("admin_glow", WrapperPlayServerTeams.TeamMode.REMOVE, null as ScoreBoardTeamInfo?, mutableListOf<String>())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        }, 200L)
    }

    // --- 🦷 H3: TRIPLE COLMILLO DEL VACÍO ---
    private fun habilidadTripleColmillo(player: Player) {
        val startLoc = player.location
        val angles = listOf(-25.0, 0.0, 25.0) // Abanico triple

        angles.forEach { offset ->
            scope.launch {
                val direction = startLoc.direction.clone().rotateAroundY(Math.toRadians(offset)).setY(0.0).normalize()
                val currentLoc = startLoc.clone()
                repeat(15) {
                    withContext(plugin.mainThread) {
                        currentLoc.add(direction)
                        if (!currentLoc.block.type.isSolid) {
                            currentLoc.world?.spawnParticle(org.bukkit.Particle.WITCH, currentLoc, 4, 0.1, 0.1, 0.1, 0.02)
                            currentLoc.world?.spawn(currentLoc, EvokerFangs::class.java)

                            // Efectos de impacto
                            currentLoc.world?.getNearbyEntities(currentLoc, 1.2, 1.2, 1.2)?.filterIsInstance<Player>()?.forEach { victim ->
                                if (!plugin.asesinoManager.esElAsesino(victim)) {
                                    plugin.combatManager.processTrueDamage(victim, player, 4.0) // 2 corazones
                                    victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0)) // 3s
                                    victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0)) // 2s
                                }
                            }
                        }
                    }
                    delay(100)
                }
            }
        }
    }

    // --- ⭐ H4: NETHER STAR (PROJECTILE) ---
    private fun habilidadNetherStar(player: Player) {
        val starDisplay = player.world.spawn(player.eyeLocation, ItemDisplay::class.java) {
            it.setItemStack(ItemStack(Material.NETHER_STAR))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.6f, 0.6f, 0.6f), Quaternionf())
            it.interpolationDuration = 1; it.teleportDuration = 1
        }

        val direction = player.location.direction.multiply(1.5)

        scope.launch {
            var ticks = 0
            withContext(plugin.mainThread) {
                while (ticks < 40 && starDisplay.isValid) {
                    starDisplay.teleport(starDisplay.location.add(direction))
                    starDisplay.world.spawnParticle(org.bukkit.Particle.END_ROD, starDisplay.location, 2, 0.0, 0.0, 0.0, 0.01)

                    val hit = starDisplay.getNearbyEntities(1.0, 1.0, 1.0).filterIsInstance<Player>().firstOrNull { !plugin.asesinoManager.esElAsesino(it) }
                    if (hit != null || starDisplay.location.block.type.isSolid) {
                        starDisplay.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, starDisplay.location, 1)
                        starDisplay.world.playSound(starDisplay.location, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.1f)

                        hit?.let {
                            plugin.combatManager.processTrueDamage(it, player, 5.0) // 2.5 corazones
                            it.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0)) // 3s debilidad
                        }
                        break
                    }
                    delay(50); ticks++
                }
                starDisplay.remove()
            }
        }
    }

    // --- 🧊 MOTOR FÍSICO: COMMAND BLOCK ORBITANTE ---
    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiar(uuid); return }

        val display = orbitadores.getOrPut(uuid) {
            player.world.spawn(player.location, BlockDisplay::class.java) {
                it.block = Material.COMMAND_BLOCK.createBlockData()
                it.transformation = Transformation(JomlVector3f(-0.2f, -0.2f, -0.2f), Quaternionf(), JomlVector3f(0.4f, 0.4f, 0.4f), Quaternionf())
                it.teleportDuration = 2; it.interpolationDuration = 2
            }
        }

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.15) % (Math.PI * 2)
        val x = 1.4 * cos(angulo); val z = 1.4 * sin(angulo); val y = 1.2 + (0.15 * sin(angulo * 2))
        display.teleport(player.location.clone().add(x, y, z))
        angulos[uuid] = angulo
    }

    override fun mostrarTrail(player: Player) {}

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemKitCache.isEmpty()) preLoadKit()

        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        inv.setItem(8, itemKitCache["arma"]?.clone())
        inv.setItem(0, itemKitCache["habilidad1"]?.clone())
        inv.setItem(1, itemKitCache["habilidad2"]?.clone())
        inv.setItem(2, itemKitCache["habilidad3"]?.clone())
        inv.setItem(3, itemKitCache["habilidad4"]?.clone())

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    private fun limpiar(uuid: UUID) {
        orbitadores.remove(uuid)?.remove()
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiar(it.uniqueId) }
        scope.coroutineContext.cancelChildren()
    }
}
