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
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * Romeo: El Administrador del Mundo.
 * FIX: Slots corregidos (0-3), Knockback manual potente y carga de armadura garantizada.
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
                if (id != "none" && id.isNotEmpty()) {
                    CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[k] = it }
                }
            }
        }

        items.forEach { k ->
            config.getString("$path.items.$k")?.let { id ->
                val name = config.getString("$path.items.${k}_nombre")
                if (id != "none" && id.isNotEmpty()) {
                    CraftEngineUtils.getCustomItem(id)?.let { item ->
                        name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                        itemKitCache[k] = item
                    }
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // 🔥 FIX: Mapping de slots 0, 1, 2, 3 (correspondientes a las teclas 1, 2, 3, 4)
        when (slot) {
            1 -> {
                if (checkCooldown(player, 1)) return
                habilidadAdminDash(player)
                reproducirEfectosHabilidad(player, 1)
            }
            2 -> {
                if (checkCooldown(player, 2)) return
                habilidadAdminVision(player)
                reproducirEfectosHabilidad(player, 2)
            }
            3 -> {
                if (checkCooldown(player, 3)) return
                habilidadTripleColmillo(player)
                reproducirEfectosHabilidad(player, 3)
            }
            4 -> {
                if (checkCooldown(player, 4)) return
                habilidadNetherStar(player)
                reproducirEfectosHabilidad(player, 4)
            }
        }
    }

    // --- 🏃 H1: ADMIN DASH CON TRUE KB ---
    private fun habilidadAdminDash(player: Player) {
        val job = scope.launch {
            var duration = 100 // 5 segundos
            withContext(plugin.mainThread) {
                player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f)
                while (duration > 0 && player.isOnline) {
                    val dir = player.location.direction.normalize().multiply(1.4)
                    player.velocity = dir

                    // Detección de colisión con muros
                    val checkLoc = player.location.clone().add(dir.clone().multiply(0.8))
                    if (checkLoc.block.type.isSolid) {
                        plugin.combatManager.processTrueDamage(player, null, 6.0) // 3 corazones daño self
                        player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                        player.sendMessage(mm.deserialize("<red><b>[!]</b> ¡Has impactado contra un muro!</red>"))
                        break
                    }

                    // Detección de víctimas + KB Manual (True Damage)
                    player.getNearbyEntities(2.0, 2.0, 2.0).filterIsInstance<Player>().firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }?.let { victim ->
                        plugin.combatManager.processTrueDamage(victim, player, 5.0) // 2.5 corazones
                        // 🔥 Knockback manual potente del dash
                        victim.velocity = player.location.direction.normalize().multiply(1.5).setY(0.4)
                        player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f)
                        duration = 0
                    }
                    delay(50); duration--
                }
            }
        }
        trackJob(job)
    }

    // --- 👁️ H2: ADMIN VISION (WHITE GLOW VIRTUAL) ---
    private fun habilidadAdminVision(player: Player) {
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("AdminTeam"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
            WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE,
            WrapperPlayServerTeams.OptionData.NONE
        )

        player.getNearbyEntities(100.0, 100.0, 100.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                val createTeam = WrapperPlayServerTeams("admin_glow", WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, mutableListOf(victim.name))
                PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)

                val mask = 0x40.toByte()
                val packet = WrapperPlayServerEntityMetadata(victim.entityId, listOf(EntityData(0, EntityDataTypes.BYTE, mask)))
                PacketEvents.getAPI().playerManager.sendPacket(player, packet)
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                val removeTeam = WrapperPlayServerTeams("admin_glow", WrapperPlayServerTeams.TeamMode.REMOVE, null as ScoreBoardTeamInfo?, mutableListOf())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        }, 200L) // 10 Segundos
    }

    // --- 🦷 H3: TRIPLE COLMILLO ---
    private fun habilidadTripleColmillo(player: Player) {
        val startLoc = player.location
        val angles = listOf(-25.0, 0.0, 25.0)

        angles.forEach { offset ->
            scope.launch {
                val direction = startLoc.direction.clone().rotateAroundY(Math.toRadians(offset)).setY(0.0).normalize()
                val currentLoc = startLoc.clone()
                repeat(15) {
                    withContext(plugin.mainThread) {
                        currentLoc.add(direction)
                        if (!currentLoc.block.type.isSolid) {
                            currentLoc.world?.spawn(currentLoc, EvokerFangs::class.java)

                            currentLoc.world?.getNearbyEntities(currentLoc, 1.5, 1.5, 1.5)?.filterIsInstance<Player>()?.forEach { victim ->
                                if (!plugin.asesinoManager.esElAsesino(victim)) {
                                    plugin.combatManager.processTrueDamage(victim, player, 4.0) // 2 corazones
                                    victim.velocity = Vector(0.0, 0.5, 0.0) // Mini KB hacia arriba
                                    victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0))
                                    victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                                }
                            }
                        }
                    }
                    delay(100)
                }
            }
        }
    }

    // --- ⭐ H4: NETHER STAR PROJECTILE ---
    private fun habilidadNetherStar(player: Player) {
        val star = player.world.spawn(player.eyeLocation, ItemDisplay::class.java) {
            it.setItemStack(ItemStack(Material.NETHER_STAR))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.7f, 0.7f, 0.7f), Quaternionf())
            it.interpolationDuration = 1; it.teleportDuration = 1
        }
        val direction = player.location.direction.multiply(1.5)
        scope.launch {
            var ticks = 0
            withContext(plugin.mainThread) {
                while (ticks < 40 && star.isValid) {
                    star.teleport(star.location.add(direction))
                    val hit = star.getNearbyEntities(1.5, 1.5, 1.5).filterIsInstance<Player>().firstOrNull { !plugin.asesinoManager.esElAsesino(it) }

                    if (hit != null || star.location.block.type.isSolid) {
                        star.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, star.location, 1)
                        star.world.playSound(star.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f)

                        hit?.let {
                            plugin.combatManager.processTrueDamage(it, player, 5.0)
                            // KB Manual de explosión
                            val kb = it.location.toVector().subtract(star.location.toVector()).normalize().multiply(1.4).setY(0.4)
                            it.velocity = kb
                            it.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0))
                        }
                        break
                    }
                    delay(50); ticks++
                }
                star.remove()
            }
        }
    }

    // --- 🧊 MOTOR FÍSICO: COMMAND BLOCK ---
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

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        display.teleport(player.location.clone().add(1.5 * cos(angulo), 1.2 + (0.15 * sin(angulo * 2)), 1.5 * sin(angulo)))
        angulos[uuid] = angulo
    }

    override fun mostrarTrail(player: Player) {}

    // --- 🛠️ EQUIPAMIENTO (FIXED) ---
    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // 🔥 FIX: Lazy load por si CraftEngine no estaba listo
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) {
            preLoadKit()
        }

        // Cargar armadura
        itemKitCache["casco"]?.let { inv.helmet = it.clone() }
        itemKitCache["pechera"]?.let { inv.chestplate = it.clone() }
        itemKitCache["pantalones"]?.let { inv.leggings = it.clone() }
        itemKitCache["botas"]?.let { inv.boots = it.clone() }

        // Cargar hotbar (Slots 0, 1, 2, 3 + Arma en 8)
        itemKitCache["habilidad1"]?.let { inv.setItem(1, it.clone()) }
        itemKitCache["habilidad2"]?.let { inv.setItem(2, it.clone()) }
        itemKitCache["habilidad3"]?.let { inv.setItem(3, it.clone()) }
        itemKitCache["habilidad4"]?.let { inv.setItem(4, it.clone()) }
        itemKitCache["arma"]?.let { inv.setItem(8, it.clone()) }

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
