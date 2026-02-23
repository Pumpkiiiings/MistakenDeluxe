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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
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

class Romeo : Asesino(
    "romeo",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.romeo.nombre", "<gradient:#ff0000:#ffff00><b>ROMEO</b></gradient>", "asesinos_info")
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
            config.getString("asesinos.romeo.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("asesinos.romeo.items.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadAdminDash(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadAdminVision(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadTripleColmillo(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadNetherStar(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            val namePath = if (key == "arma") "asesinos.romeo.habilidades_nombres.arma"
            else "asesinos.romeo.habilidades_nombres.$key"

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

    // --- 🏃 H1: ADMIN DASH ---
    private fun habilidadAdminDash(player: Player) {
        val job = scope.launch {
            var duration = 100 // 5 segundos
            withContext(plugin.bukkitDispatcher) {
                player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f)
                while (isActive && duration > 0 && player.isOnline) {
                    val dir = player.location.direction.normalize().multiply(1.4)
                    player.velocity = dir

                    val checkLoc = player.location.clone().add(dir.clone().multiply(0.8))
                    if (checkLoc.block.type.isSolid) {
                        plugin.gameManager.combatManager.takeDamage(player) // Daño al chocar
                        player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                        break
                    }

                    val hit = player.getNearbyEntities(2.0, 2.0, 2.0).filterIsInstance<Player>().firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }

                    hit?.let { victim ->
                        plugin.gameManager.combatManager.takeDamage(victim)
                        victim.velocity = player.location.direction.normalize().multiply(1.5).setY(0.4)
                        player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f)
                        duration = 0 // Cortar dash
                    }
                    delay(50); duration--
                }
            }
        }
        trackJob(job)
    }

    // --- 👁️ H2: ADMIN VISION (FIX TEAMS) ---
    private fun habilidadAdminVision(player: Player) {
        val teamName = "admin_glow"
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("AdminTeam"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.NONE
        )

        player.getNearbyEntities(100.0, 100.0, 100.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                val createTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, listOf(victim.name))
                PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)
                val packet = WrapperPlayServerEntityMetadata(victim.entityId, listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte())))
                PacketEvents.getAPI().playerManager.sendPacket(player, packet)
            }
        }

        val job = scope.launch {
            delay(10000)
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline) {
                    val removeTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty())
                    PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
                }
            }
        }
        trackJob(job)
    }

    // --- 🦷 H3: TRIPLE COLMILLO ---
    private fun habilidadTripleColmillo(player: Player) {
        val startLoc = player.location
        val angles = listOf(-25.0, 0.0, 25.0)

        angles.forEach { offset ->
            val job = scope.launch {
                val direction = startLoc.direction.clone().rotateAroundY(Math.toRadians(offset)).setY(0.0).normalize()
                val currentLoc = startLoc.clone()
                repeat(15) {
                    if (!isActive) return@launch
                    withContext(plugin.bukkitDispatcher) {
                        currentLoc.add(direction)
                        if (!currentLoc.block.type.isSolid) {
                            currentLoc.world.spawn(currentLoc, EvokerFangs::class.java)
                            currentLoc.world.getNearbyEntities(currentLoc, 1.5, 1.5, 1.5).filterIsInstance<Player>().forEach { victim ->
                                if (!plugin.asesinoManager.esElAsesino(victim)) {
                                    plugin.gameManager.combatManager.takeDamage(victim)
                                    victim.velocity = Vector(0.0, 0.5, 0.0)
                                    victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0))
                                    victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                                }
                            }
                        }
                    }
                    delay(100)
                }
            }
            trackJob(job)
        }
    }

    // --- ⭐ H4: NETHER STAR ---
    private fun habilidadNetherStar(player: Player) {
        val star = player.world.spawn(player.eyeLocation, ItemDisplay::class.java) {
            it.setItemStack(ItemStack(Material.NETHER_STAR))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.7f, 0.7f, 0.7f), Quaternionf())
            it.interpolationDuration = 1; it.teleportDuration = 1
        }
        val direction = player.location.direction.multiply(1.5)

        val job = scope.launch {
            var ticks = 0
            while (isActive && ticks < 40 && star.isValid) {
                withContext(plugin.bukkitDispatcher) {
                    star.teleport(star.location.add(direction))
                    val hit = star.getNearbyEntities(1.5, 1.5, 1.5).filterIsInstance<Player>().firstOrNull { !plugin.asesinoManager.esElAsesino(it) }

                    if (hit != null || star.location.block.type.isSolid) {
                        star.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, star.location, 1)
                        star.world.playSound(star.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f)

                        hit?.let {
                            plugin.gameManager.combatManager.takeDamage(it)
                            it.velocity = it.location.toVector().subtract(star.location.toVector()).normalize().multiply(1.4).setY(0.4)
                            it.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0))
                        }
                        star.remove()
                        cancel()
                    }
                }
                delay(50); ticks++
            }
            withContext(plugin.bukkitDispatcher) { if (star.isValid) star.remove() }
        }
        trackJob(job)
    }

    // --- 🧊 MOTOR FÍSICO ---
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
        display.teleport(player.location.clone().add(1.5 * cos(angulo), 1.2 + (0.2 * sin(angulo * 2)), 1.5 * sin(angulo)))
        angulos[uuid] = angulo
    }

    override fun mostrarTrail(player: Player) {} // Romeo no usa partículas, solo el bloque físico

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
