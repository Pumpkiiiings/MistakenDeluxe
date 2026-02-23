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
 * Optimizado: Compatibilidad total con I18n Multi-Archivo y Coroutines.
 */
class Bendy : Asesino(
    "bendy",
    // Jalamos el nombre base por defecto desde el asesinos_info (El multi-lenguaje lo aplica el Scoreboard/Tienda en vivo)
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.bendy.nombre", "<black><b>BENDY</b>", "asesinos_info")
) {

    private val path = "asesinos.bendy"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var isBeastMode = false
    private val auras = ConcurrentHashMap<UUID, BlockDisplay>()

    init {
        preLoadKit()
    }

    /**
     * 🔥 PRE-LOAD (Mecánicas Globales):
     * Aquí SOLO leemos el 'asesinos.yml' para sacar los materiales (CraftEngine o Vanilla).
     * Nada de nombres, eso se pone a la hora de equipar.
     */
    private fun preLoadKit() {
        // Usamos el ConfigManager para sacar el global de mecánicas
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$path.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
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

    // --- 🌀 HABILIDADES (Optimizadas con bukkitDispatcher) ---

    private fun habilidadInkPortal(player: Player) {
        val target = player.getTargetBlockExact(15) ?: player.location.add(player.location.direction.multiply(5)).block
        val targetLoc = target.location.add(0.5, 1.1, 0.5)

        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 40, 4, false, false))
        player.world.spawnParticle(org.bukkit.Particle.SQUID_INK, player.location, 50, 0.5, 1.0, 0.5, 0.1)
        player.playSound(player.location, Sound.ENTITY_SQUID_SQUIRT, 1f, 0.5f)

        val job = scope.launch {
            delay(1500)
            withContext(plugin.bukkitDispatcher) { // 🔥 Fix del Thread
                if (player.isOnline) {
                    player.teleport(targetLoc)
                    player.world.spawnParticle(org.bukkit.Particle.SQUID_INK, player.location, 50, 0.5, 1.0, 0.5, 0.1)

                    player.getNearbyEntities(4.0, 4.0, 4.0).filterIsInstance<Player>().forEach { victim ->
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
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
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
                    puddle.world.getNearbyEntities(puddle.location, 1.0, 1.0, 1.0).filterIsInstance<Player>().forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 1))
                        }
                    }
                }
                delay(100) // 2 ticks
                duration--
            }
            if (puddle.isValid) {
                withContext(plugin.bukkitDispatcher) { puddle.remove() }
            }
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

    // --- 🛠️ EQUIPAMIENTO (Traducción al Vuelo) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemKitCache.isEmpty()) preLoadKit()

        // 1. Obtenemos el archivo de idiomas del jugador (asesinos_info.yml)
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun giveNamedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            // 🔥 LA MAGIA: Jalamos el nombre según la nueva estructura de tu YAML
            val namePath = "asesinos.bendy.habilidades_nombres.$key"
            val localizedName = langInfo.getString(namePath)

            if (localizedName != null) {
                item.editMeta { it.displayName(mm.deserialize(localizedName)) }
            }

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

        // Armadura
        giveNamedItem(0, "casco", true)
        giveNamedItem(0, "pechera", true)
        giveNamedItem(0, "pantalones", true)
        giveNamedItem(0, "botas", true)

        // Habilidades
        giveNamedItem(1, "habilidad1")
        giveNamedItem(2, "habilidad2")
        giveNamedItem(3, "habilidad3")
        giveNamedItem(4, "habilidad4")
        giveNamedItem(8, "arma")

        player.inventory.heldItemSlot = 8
        player.updateInventory()
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

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        isBeastMode = false
        player?.let { limpiarAura(it.uniqueId) }
        scope.coroutineContext.cancelChildren()
    }
}
