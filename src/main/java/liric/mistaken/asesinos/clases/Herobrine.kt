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
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * Herobrine: El Rey del Vacío.
 * MEJORA: Animación Ultra-Fluida y Efectos Pulidos.
 */
class Herobrine : Asesino(
    "herobrine",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.herobrine.nombre", "<white><b>HEROBRINE</b>", "asesinos_info")
) {

    private val pathBase = "asesinos.herobrine"
    private val blockOrbiters = ConcurrentHashMap<UUID, BlockDisplay>()
    private val itemOrbiters = ConcurrentHashMap<UUID, MutableList<Entity>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    // --- HABILIDADES (Mismo código optimizado) ---

    private fun habilidadDashVacio(player: Player) {
        val dir = player.location.direction.normalize()
        player.velocity = dir.clone().multiply(2.5).setY(0.2)
        player.world.spawnParticle(org.bukkit.Particle.FLASH, player.location.add(0.0, 1.0, 0.0), 5, 0.2, 0.2, 0.2, 0.0)

        val job = scope.launch {
            var ticks = 0
            val hitted = mutableSetOf<UUID>()
            while (isActive && ticks < 12 && player.isOnline) {
                withContext(plugin.bukkitDispatcher) {
                    player.world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, player.location, 3, 0.1, 0.1, 0.1, 0.02)
                    player.world.spawnParticle(org.bukkit.Particle.WHITE_SMOKE, player.location, 2, 0.05, 0.05, 0.05, 0.01)

                    val eyeLoc = player.eyeLocation.add(dir.clone().multiply(0.8))
                    if (eyeLoc.block.type.isSolid) {
                        player.sendMessage(mm.deserialize("<red><b>[!]</b> ¡Te estampaste contra el muro!"))
                        repeat(3) { plugin.gameManager.combatManager.takeDamage(player) }
                        player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                        cancel()
                        return@withContext
                    }

                    player.getNearbyEntities(1.5, 1.5, 1.5).filterIsInstance<Player>().forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim) && victim.uniqueId !in hitted) {
                            hitted.add(victim.uniqueId)
                            repeat(3) { plugin.gameManager.combatManager.takeDamage(victim) }
                            victim.playSound(victim.location, Sound.ENTITY_WITHER_BREAK_BLOCK, 1f, 0.8f)
                            victim.sendMessage(mm.deserialize("<red><b>[!]</b> Herobrine te ha embestido con el poder del Vacío."))
                        }
                    }
                }
                delay(50)
                ticks++
            }
        }
        trackJob(job)
    }

    private fun habilidadSaltoDimensional(player: Player) {
        val gens = plugin.generatorManager.getGeneratorLocations()
        if (gens.isEmpty()) return
        player.world.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, player.location.add(0.0, 1.0, 0.0), 30, 0.5, 1.0, 0.5, 0.1)
        player.playSound(player.location, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 0.5f)
        val target = gens.random().clone().add(0.5, 1.1, 0.5)
        player.teleport(target)
        player.world.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, player.location.add(0.0, 1.0, 0.0), 25, 0.4, 0.8, 0.4, 0.05)
        player.playSound(player.location, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.8f)
    }

    private fun habilidadEstrellaWither(player: Player) {
        val skull = player.launchProjectile(WitherSkull::class.java)
        skull.yield = 0f
        val job = scope.launch {
            var life = 0
            while (isActive && life < 60 && !skull.isDead) {
                withContext(plugin.bukkitDispatcher) {
                    skull.world.spawnParticle(org.bukkit.Particle.WITCH, skull.location, 3, 0.05, 0.05, 0.05, 0.01)
                    val hit = player.world.getNearbyPlayers(skull.location, 1.2).firstOrNull { !plugin.asesinoManager.esElAsesino(it) }
                    hit?.let {
                        plugin.gameManager.combatManager.takeDamage(it)
                        it.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
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
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("HB_Team"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.DARK_PURPLE, WrapperPlayServerTeams.OptionData.NONE
        )

        Bukkit.getOnlinePlayers().forEach { online ->
            if (plugin.asesinoManager.esElAsesino(online)) return@forEach
            val createTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, listOf(online.name))
            PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)
            val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
            PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(online.entityId, metadata))
            online.playSound(online.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f)
            online.world.spawnParticle(org.bukkit.Particle.ENCHANTED_HIT, online.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)
        }

        scope.launch {
            delay(10000)
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline) {
                    val removeTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty())
                    PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
                }
            }
        }
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

            val namePath = if (key == "arma") "asesinos.herobrine.habilidades_nombres.arma"
            else "asesinos.herobrine.habilidades_nombres.$key"

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

    // --- 🔥 ANIMACIÓN FÍSICA ULTRA-FLUIDA (Butter Smooth) ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }

        // Reiniciar si cambia de mundo
        if (blockOrbiters[uuid]?.world != player.world) limpiarVisuales(uuid)

        // 1. Inicialización de entidades
        if (!blockOrbiters.containsKey(uuid)) {
            val bMain = player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                bd.block = Material.NETHERRACK.createBlockData()
                bd.transformation = Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf())
                bd.teleportDuration = 3 // 🔥 TRUCO DE FLUIDEZ
                bd.interpolationDuration = 3
            }
            blockOrbiters[uuid] = bMain

            val extras = mutableListOf<Entity>().apply {
                add(player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                    id.setItemStack(ItemStack(Material.NETHER_STAR))
                    id.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
                    id.teleportDuration = 3
                    id.interpolationDuration = 3
                })
                add(player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                    bd.block = Material.GOLD_BLOCK.createBlockData()
                    bd.transformation = Transformation(JomlVector3f(-0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf())
                    bd.teleportDuration = 3
                    bd.interpolationDuration = 3
                })
            }
            itemOrbiters[uuid] = extras
        }

        // 2. Cálculos en memoria
        val anguloBase = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        val radio = 1.4
        val pLoc = player.location

        // 3. Movimiento Sincronizado
        val bMain = blockOrbiters[uuid]!!
        val extras = itemOrbiters[uuid]!!

        // Bloque Principal (Netherrack)
        val loc1 = pLoc.clone().add(radio * cos(anguloBase), 1.2 + (0.2 * sin(anguloBase * 2)), radio * sin(anguloBase))
        loc1.yaw = (anguloBase * 100).toFloat() % 360 // Rotación propia
        bMain.teleport(loc1)

        // Item Estrella
        val angle2 = anguloBase + 2.09
        val loc2 = pLoc.clone().add(radio * cos(angle2), 1.0 + (0.2 * cos(anguloBase * 2)), radio * sin(angle2))
        loc2.yaw = (angle2 * 80).toFloat() % 360
        extras[0].teleport(loc2)

        // Bloque Oro
        val angle3 = anguloBase + 4.18
        val loc3 = pLoc.clone().add(radio * cos(angle3), 0.8 + (0.2 * sin(anguloBase)), radio * sin(angle3))
        loc3.yaw = (angle3 * 100).toFloat() % 360
        extras[1].teleport(loc3)

        angulos[uuid] = anguloBase
    }

    override fun mostrarTrail(player: Player) {
        val l = player.location.add(0.0, 1.2, 0.0)
        val packet = WrapperPlayServerParticle(
            Particle(ParticleTypes.CLOUD), false,
            Vector3d(l.x, l.y, l.z),
            Vector3f(0.12f, 0.12f, 0.12f),
            0.01f,
            1
        )
        // Enviar solo a los que están cerca (ahorro de red)
        player.world.players.forEach {
            if (it.location.distanceSquared(l) < 400.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet)
        }
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
