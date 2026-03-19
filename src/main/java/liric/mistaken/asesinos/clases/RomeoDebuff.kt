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
 * RomeoDebuff: El Administrador del Mundo (Versión Balanceada).
 * NERFEOS: Menos rango de visión, un solo colmillo en lugar de triple, y dash más corto.
 */
class RomeoDebuff : Asesino(
    "romeodebuff",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.romeodebuff.nombre", "<gradient:#ff5555:#ffff55><b>ROMEO (NERF)</b></gradient>", "asesinos_info")
) {

    private val pathBase = "asesinos.romeodebuff"
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
        if (checkCooldown(player, slot)) return
        when (slot) {
            1 -> { habilidadAdminDash(player); reproducirEfectosHabilidad(player, 1) }
            2 -> { habilidadAdminVision(player); reproducirEfectosHabilidad(player, 2) }
            3 -> { habilidadUnicoColmillo(player); reproducirEfectosHabilidad(player, 3) } // Nerf
            4 -> { habilidadNetherStar(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    private fun habilidadAdminDash(player: Player) {
        player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f)

        // 🔴 NERF: Reducido de 100 iteraciones a 10 (Dash mucho más corto y táctico)
        var duration = 10

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (duration <= 0 || !player.isOnline) {
                task.cancel()
                return@Consumer
            }
            // 🔴 NERF: Velocidad reducida de 1.4 a 1.0
            val dir = player.location.direction.normalize().multiply(1.0)
            player.velocity = dir

            val checkLoc = player.location.clone().add(dir.clone().multiply(0.8))
            if (checkLoc.block.type.isSolid) {
                plugin.gameManager.combatManager.takeDamage(player)
                player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                task.cancel()
                return@Consumer
            }

            player.world.getNearbyPlayers(player.location, 2.0).firstOrNull { esObjetivoValido(player, it) }?.let { victim ->
                plugin.gameManager.combatManager.takeDamage(victim)
                // 🔴 NERF: Empuje (KB) reducido
                victim.velocity = player.location.direction.normalize().multiply(1.0).setY(0.4)
                player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f)
                duration = 0
            }
            duration--
        }, null, 1L, 1L)
    }

    private fun habilidadAdminVision(player: Player) {
        val teamName = "admind_glow" // Diferente team para evitar colisión con el Romeo Original
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("AdminTeam"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.NONE
        )

        // 🔴 NERF: Radio de visión bajado de 100.0 a 30.0 bloques
        player.world.getNearbyPlayers(player.location, 30.0).forEach { victim ->
            if (esObjetivoValido(player, victim)) {
                val createTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, listOf(victim.name))
                PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)
                val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
                PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(victim.entityId, metadata))
            }
        }

        // 🔴 NERF: Duración bajada de 10 segundos (200L) a 5 segundos (100L)
        player.scheduler.runDelayed(plugin, Consumer { _ ->
            if (player.isOnline) {
                val removeTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        }, null, 100L)
    }

    private fun habilidadUnicoColmillo(player: Player) {
        val startLoc = player.location
        // 🔴 NERF: Ya no dispara 3 líneas (TripleColmillo). Solo dispara 1 línea central recta.
        val direction = startLoc.direction.clone().setY(0.0).normalize()
        val currentLoc = startLoc.clone()

        // 🔴 NERF: El alcance máximo bajado de 15 a 10 colmillos de distancia
        for (i in 0 until 10) {
            currentLoc.add(direction)
            val locToSpawn = currentLoc.clone()
            plugin.server.regionScheduler.runDelayed(plugin, locToSpawn, Consumer { _ ->
                if (!locToSpawn.block.type.isSolid) {
                    locToSpawn.world.spawn(locToSpawn, EvokerFangs::class.java)
                    locToSpawn.world.getNearbyPlayers(locToSpawn, 1.5).forEach { victim ->
                        if (esObjetivoValido(player, victim)) {
                            plugin.gameManager.combatManager.takeDamage(victim)
                            victim.velocity = Vector(0.0, 0.5, 0.0)
                            // 🔴 NERF: Wither en lugar de Darkness ceguera total
                            victim.addPotionEffect(PotionEffect(PotionEffectType.WITHER, 40, 0))
                        }
                    }
                }
            }, (i * 2 + 1).toLong())
        }
    }

    private fun habilidadNetherStar(player: Player) {
        val star = player.world.spawn(player.eyeLocation, ItemDisplay::class.java) {
            it.setItemStack(ItemStack(Material.NETHER_STAR))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf()) // Más pequeña
            it.interpolationDuration = 1; it.teleportDuration = 1
        }
        // 🔴 NERF: Velocidad del proyectil bajada de 1.5 a 0.8
        val direction = player.location.direction.multiply(0.8)

        var ticks = 0
        star.scheduler.runAtFixedRate(plugin, Consumer { task ->
            // 🔴 NERF: Desaparece a los 25 ticks (aprox 1.25 segundos) si no choca
            if (ticks >= 25 || !star.isValid) {
                if (star.isValid) star.remove()
                task.cancel()
                return@Consumer
            }
            star.teleport(star.location.add(direction))

            val hit = player.world.getNearbyPlayers(star.location, 1.2).firstOrNull { esObjetivoValido(player, it) }

            if (hit != null || star.location.block.type.isSolid) {
                star.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, star.location, 1)
                hit?.let { plugin.gameManager.combatManager.takeDamage(it) }
                star.remove()
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }

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

            val namePath = if (key == "arma") "asesinos.romeodebuff.habilidades_nombres.arma"
            else "asesinos.romeodebuff.habilidades_nombres.$key"

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

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiar(uuid); return }

        val playerWorld = player.world
        if (orbitadores[uuid]?.world != playerWorld) limpiar(uuid)

        val display = orbitadores.getOrPut(uuid) {
            player.world.spawn(player.location, BlockDisplay::class.java) {
                // 🔴 NERF VISUAL: Para distinguirlo del Romeo OP, su bloque orbitante es un bloque normal de piedra o similar,
                // pero si quieres mantenerlo igual solo cambia COBBLESTONE por COMMAND_BLOCK
                it.block = Material.COBBLESTONE.createBlockData()
                it.transformation = Transformation(
                    JomlVector3f(-0.15f, -0.15f, -0.15f),
                    Quaternionf(),
                    JomlVector3f(0.3f, 0.3f, 0.3f), // Un 25% más pequeño que el OP
                    Quaternionf()
                )
                it.teleportDuration = 3
                it.interpolationDuration = 3
            }
        }

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.10) % (Math.PI * 2) // Gira un poquito más lento
        val pLoc = player.location
        val radio = 1.3

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
