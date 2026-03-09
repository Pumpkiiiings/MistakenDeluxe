package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
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
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * Romeo: El Administrador del Mundo.
 * FIX: Animación Fluida y sin Corrutinas.
 */
class Romeo : Asesino(
    "romeo",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.romeo.nombre", "<gradient:#ff0000:#ffff00><b>ROMEO</b></gradient>", "asesinos_info")
) {

    private val pathBase = "asesinos.romeo"
    private val orbitadores = ConcurrentHashMap<UUID, BlockDisplay>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorMap = mapOf(
            "casco" to Material.NETHERITE_HELMET,
            "pechera" to Material.NETHERITE_CHESTPLATE,
            "pantalones" to Material.NETHERITE_LEGGINGS,
            "botas" to Material.NETHERITE_BOOTS
        )

        armorMap.forEach { (key, fallbackMat) ->
            val id = config.getString("$pathBase.armadura.$key") ?: "none"
            if (id != "none" && id.isNotBlank()) {
                val item = CraftEngineUtils.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: fallbackMat)
            }
        }

        listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4").forEach { key ->
            val id = config.getString("$pathBase.items.$key") ?: "none"
            if (id != "none" && id.isNotBlank()) {
                val item = CraftEngineUtils.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.PAPER)
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

    // --- HABILIDADES ---

    private fun habilidadAdminDash(player: Player) {
        player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f)
        var duration = 100

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (duration <= 0 || !player.isOnline) {
                task.cancel()
                return@Consumer
            }
            val dir = player.location.direction.normalize().multiply(1.4)
            player.velocity = dir

            val checkLoc = player.location.clone().add(dir.clone().multiply(0.8))
            if (checkLoc.block.type.isSolid) {
                plugin.gameManager.combatManager.takeDamage(player)
                player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                task.cancel()
                return@Consumer
            }

            player.world.getNearbyPlayers(player.location, 2.0).firstOrNull {
                !plugin.asesinoManager.esElAsesino(it)
            }?.let { victim ->
                plugin.gameManager.combatManager.takeDamage(victim)
                victim.velocity = player.location.direction.normalize().multiply(1.5).setY(0.4)
                player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f)
                duration = 0 // Fuerza la cancelación en la siguiente iteración
            }
            duration--
        }, null, 1L, 1L)
    }

    private fun habilidadAdminVision(player: Player) {
        val teamName = "admin_glow"
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("AdminTeam"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.NONE
        )

        player.world.getNearbyPlayers(player.location, 100.0).forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                val createTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, listOf(victim.name))
                PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)
                val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
                PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(victim.entityId, metadata))
            }
        }

        // 10000ms = 200 ticks
        player.scheduler.runDelayed(plugin, Consumer { _ ->
            if (player.isOnline) {
                val removeTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        }, null, 200L)
    }

    private fun habilidadTripleColmillo(player: Player) {
        val startLoc = player.location
        val angles = listOf(-25.0, 0.0, 25.0)

        angles.forEach { offset ->
            val direction = startLoc.direction.clone().rotateAroundY(Math.toRadians(offset)).setY(0.0).normalize()
            val currentLoc = startLoc.clone()

            for (i in 0 until 15) {
                currentLoc.add(direction)
                val locToSpawn = currentLoc.clone()
                plugin.server.regionScheduler.runDelayed(plugin, locToSpawn, Consumer { _ ->
                    if (!locToSpawn.block.type.isSolid) {
                        locToSpawn.world.spawn(locToSpawn, EvokerFangs::class.java)
                        locToSpawn.world.getNearbyPlayers(locToSpawn, 1.5).forEach { victim ->
                            if (!plugin.asesinoManager.esElAsesino(victim)) {
                                plugin.gameManager.combatManager.takeDamage(victim)
                                victim.velocity = Vector(0.0, 0.5, 0.0)
                                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                            }
                        }
                    }
                }, (i * 2 + 1).toLong()) // 100ms = 2 ticks de diferencia
            }
        }
    }

    private fun habilidadNetherStar(player: Player) {
        val star = player.world.spawn(player.eyeLocation, ItemDisplay::class.java) {
            it.setItemStack(ItemStack(Material.NETHER_STAR))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.7f, 0.7f, 0.7f), Quaternionf())
            it.interpolationDuration = 1; it.teleportDuration = 1
        }
        val direction = player.location.direction.multiply(1.5)

        var ticks = 0
        star.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 40 || !star.isValid) {
                if (star.isValid) star.remove()
                task.cancel()
                return@Consumer
            }
            star.teleport(star.location.add(direction))
            val hit = player.world.getNearbyPlayers(star.location, 1.5).firstOrNull { !plugin.asesinoManager.esElAsesino(it) }
            if (hit != null || star.location.block.type.isSolid) {
                star.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, star.location, 1)
                hit?.let { plugin.gameManager.combatManager.takeDamage(it) }
                star.remove()
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }

    // --- EQUIPAMIENTO ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val configMecanica = plugin.configManager.getAsesinos()
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
            else configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "arma") "asesinos.romeo.habilidades_nombres.arma"
            else "asesinos.romeo.habilidades_nombres.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
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

        deliver("casco", 0, true); deliver("pechera", 0, true)
        deliver("pantalones", 0, true); deliver("botas", 0, true)
        deliver("habilidad1", 1); deliver("habilidad2", 2)
        deliver("habilidad3", 3); deliver("habilidad4", 4)
        deliver("arma", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // --- 🔥 ANIMACIÓN DE ÓRBITA ULTRA-FLUIDA ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiar(uuid); return }

        val playerWorld = player.world
        if (orbitadores[uuid]?.world != playerWorld) limpiar(uuid)

        val display = orbitadores.getOrPut(uuid) {
            player.world.spawn(player.location, BlockDisplay::class.java) {
                it.block = Material.COMMAND_BLOCK.createBlockData()
                it.transformation = Transformation(
                    JomlVector3f(-0.2f, -0.2f, -0.2f),
                    Quaternionf(),
                    JomlVector3f(0.4f, 0.4f, 0.4f),
                    Quaternionf()
                )
                it.teleportDuration = 3
                it.interpolationDuration = 3
            }
        }

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        val pLoc = player.location
        val radio = 1.5

        val x = radio * cos(angulo)
        val z = radio * sin(angulo)
        val y = 1.2 + (0.2 * sin(angulo * 2))

        val targetLoc = pLoc.clone().add(x, y, z)
        targetLoc.yaw = (angulo * 120).toFloat() % 360
        targetLoc.pitch = (angulo * 60).toFloat() % 360

        display.teleport(targetLoc)

        angulos[uuid] = angulo
    }

    override fun mostrarTrail(player: Player) {}

    private fun limpiar(uuid: UUID) {
        orbitadores.remove(uuid)?.remove()
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiar(it.uniqueId) }
    }
}
