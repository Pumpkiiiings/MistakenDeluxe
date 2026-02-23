package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
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

/**
 * [LIRIC-MISTAKEN 2.0]
 * Bendy: El Demonio de Tinta.
 * FIX: Nuevo sistema de equipamiento robusto y multi-idioma.
 */
class Bendy : Asesino(
    "bendy",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.bendy.nombre", "<black><b>BENDY</b>", "asesinos_info")
) {

    private val pathBase = "asesinos.bendy"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isBeastMode = false
    private val auras = ConcurrentHashMap<UUID, BlockDisplay>()

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadInkPortal(player); reproducirEfectosHabilidad(player, 1); applyInkFatigue(player) }
            2 -> if (!checkCooldown(player, 2)) { habilidadInkFlow(player); reproducirEfectosHabilidad(player, 2); applyInkFatigue(player) }
            3 -> if (!checkCooldown(player, 3)) { habilidadInkPuddle(player); reproducirEfectosHabilidad(player, 3); applyInkFatigue(player) }
            4 -> if (!checkCooldown(player, 4)) { habilidadTheBeast(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    private fun applyInkFatigue(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false, true))
    }

    /**
     * 🔥 NUEVO MÉTODO EQUIPAR (REVISADO):
     * Jala los fierros de asesinos.yml y los nombres de asesinos_info.yml
     */
    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4) // Limpieza total de armadura

        val configMecanica = plugin.configManager.getAsesinos() // El global (la raíz)
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info") // Los nombres traducidos

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            // 1. Buscamos el ID en el archivo global de mecánicas
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
            else configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            // 2. Intentamos crear el ítem (CraftEngine o Vanilla)
            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            // 3. Le ponemos el nombre según el idioma del jugador
            val namePath = if (key == "arma") "asesinos.bendy.habilidades_nombres.arma"
            else "asesinos.bendy.habilidades_nombres.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
            }

            // 4. Lo ponemos en su lugar
            if (isArmor) {
                when(key) {
                    "casco" -> inv.helmet = item
                    "pechera" -> inv.chestplate = item
                    "pantalones" -> inv.leggings = item
                    "botas" -> inv.boots = item
                }
            } else {
                inv.setItem(slot, item)
            }
        }

        // --- EJECUCIÓN DEL EQUIPO ---
        deliver("casco", 0, true)
        deliver("pechera", 0, true)
        deliver("pantalones", 0, true)
        deliver("botas", 0, true)

        deliver("habilidad1", 1)
        deliver("habilidad2", 2)
        deliver("habilidad3", 3)
        deliver("habilidad4", 4)
        deliver("arma", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // --- 🌀 HABILIDADES ---

    private fun habilidadInkPortal(player: Player) {
        val target = player.getTargetBlockExact(15) ?: player.location.add(player.location.direction.multiply(5.0)).block
        val targetLoc = target.location.add(0.5, 1.1, 0.5)

        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 40, 4, false, false))
        player.world.spawnParticle(org.bukkit.Particle.SQUID_INK, player.location, 50, 0.5, 1.0, 0.5, 0.1)
        player.playSound(player.location, Sound.ENTITY_SQUID_SQUIRT, 1f, 0.5f)

        val job = scope.launch {
            delay(1500)
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline) {
                    player.teleport(targetLoc)
                    player.world.spawnParticle(org.bukkit.Particle.SQUID_INK, player.location, 50, 0.5, 1.0, 0.5, 0.1)
                    player.world.getNearbyPlayers(player.location, 4.0).forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.1f)
                            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                        }
                    }
                }
            }
        }
        trackJob(job)
    }

    private fun habilidadInkFlow(player: Player) {
        player.world.getNearbyPlayers(player.location, 8.0).forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                val toVictim = victim.location.toVector().subtract(player.location.toVector()).normalize()
                if (player.location.direction.dot(toVictim) > 0.7) {
                    victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 0))
                }
            }
        }
    }

    private fun habilidadInkPuddle(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.05, 0.5)
        val puddle = player.world.spawn(loc, BlockDisplay::class.java) {
            it.block = Material.BLACK_CONCRETE.createBlockData()
            it.transformation = Transformation(JomlVector3f(-0.75f, 0f, -0.75f), Quaternionf(), JomlVector3f(1.5f, 0.02f, 1.5f), Quaternionf())
        }
        val job = scope.launch {
            var duration = 200
            while (isActive && duration > 0 && puddle.isValid) {
                withContext(plugin.bukkitDispatcher) {
                    puddle.world.getNearbyPlayers(puddle.location, 1.0).forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 1))
                        }
                    }
                }
                delay(100)
                duration--
            }
            if (isActive) withContext(plugin.bukkitDispatcher) { puddle.remove() }
        }
        trackJob(job)
    }

    private fun habilidadTheBeast(player: Player) {
        isBeastMode = true
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 200, 1))
        player.isGlowing = true
        val job = scope.launch {
            delay(10000)
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline) {
                    isBeastMode = false
                    player.isGlowing = false
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                    applyInkFatigue(player)
                }
            }
        }
        trackJob(job)
    }

    // --- 🚀 MOTORES VISUALES ---

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val loc = player.location
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val mgr = PacketEvents.getAPI().playerManager

        val ground = WrapperPlayServerParticle(Particle(ParticleTypes.SQUID_INK), false, pos.add(0.0, 0.1, 0.0), Vector3f(0.3f, 0.0f, 0.3f), 0.02f, 2)
        val drips = WrapperPlayServerParticle(Particle(ParticleTypes.LARGE_SMOKE), false, pos.add(0.0, 1.8, 0.0), Vector3f(0.2f, 0.4f, 0.2f), 0.01f, 1)

        loc.world.players.forEach { viewer ->
            if (viewer.location.distanceSquared(loc) < 400.0) {
                mgr.sendPacket(viewer, ground)
                mgr.sendPacket(viewer, drips)
            }
        }
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarAura(uuid); return }
        if (auras[uuid]?.world != player.world) limpiarAura(uuid)

        val display = auras.getOrPut(uuid) {
            player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                bd.block = Material.BLACK_CONCRETE.createBlockData()
                bd.transformation = Transformation(JomlVector3f(-0.6f, 0.01f, -0.6f), Quaternionf(), JomlVector3f(1.2f, 0.02f, 1.2f), Quaternionf())
                bd.teleportDuration = 2; bd.interpolationDuration = 2
            }
        }
        display.teleport(player.location.clone().add(0.0, 0.01, 0.0))
    }

    private fun limpiarAura(uuid: UUID) { auras.remove(uuid)?.remove() }
    override fun cleanup(player: Player?) { super.cleanup(player); isBeastMode = false; player?.let { limpiarAura(it.uniqueId) }; scope.coroutineContext.cancelChildren() }
}
