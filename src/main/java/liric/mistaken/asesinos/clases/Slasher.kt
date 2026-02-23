package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.mainThread
import org.bukkit.*
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
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
 * Slasher (Pumpkin White): El carnicero implacable.
 * FIX: Slots 0-3, ItemDisplay para Machete, Multi-Lang y True Damage.
 */
class Slasher : Asesino(
    "slasher",
    Mistaken.Companion.instance.messageConfig.getSpecificFile(null, "asesinos").getString("asesinos.slasher.nombre", "Slasher")!!
) {

    private val path = "asesinos.slasher"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val temporaryEntities = ConcurrentHashMap.newKeySet<Entity>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinosConfig(null) // Archivo raíz
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("asesinos.slasher.armadura.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                }
            }
        }

        items.forEach { k ->
            config.getString("asesinos.slasher.items.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // 🔥 FIX SLOTS: 0, 1, 2, 3 corresponden a las teclas 1, 2, 3, 4
        when (slot) {
            0 -> if (!checkCooldown(player, 1)) { habilidadSedDeSangre(player); reproducirEfectosHabilidad(player, 1) }
            1 -> if (!checkCooldown(player, 2)) { habilidadMacheteLanzable(player); reproducirEfectosHabilidad(player, 2) }
            2 -> if (!checkCooldown(player, 3)) { habilidadPresencia(player); reproducirEfectosHabilidad(player, 3) }
            3 -> if (!checkCooldown(player, 4)) { habilidadEjecucion(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🩸 H1: SED DE SANGRE ---
    private fun habilidadSedDeSangre(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 160, 2))
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 160, 1))
        dibujarEstrella(player, Color.RED, 1.5, 5)

        val job = scope.launch {
            delay(8000) // 8 segundos
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 1))
                }
            }
        }
        trackJob(job)
    }

    // --- 🗡️ H2: MACHETE LANZABLE (ItemDisplay 1.21.4) ---
    private fun habilidadMacheteLanzable(player: Player) {
        val macheteItem = itemKitCache["arma"]?.clone() ?: ItemStack(Material.IRON_SWORD)
        val spawnLoc = player.eyeLocation.clone()

        val machete = player.world.spawn(spawnLoc, ItemDisplay::class.java) { id ->
            id.setItemStack(macheteItem)
            id.setTransformation(Transformation(JomlVector3f(), Quaternionf().rotateX(Math.toRadians(90.0).toFloat()), JomlVector3f(0.7f, 0.7f, 0.7f), Quaternionf()))
            id.interpolationDuration = 1; id.teleportDuration = 1
        }

        temporaryEntities.add(machete)
        val direction = player.location.direction.multiply(1.4)

        scope.launch {
            var ticks = 0
            withContext(plugin.bukkitDispatcher) {
                while (ticks < 30 && machete.isValid) {
                    machete.teleport(machete.location.add(direction))

                    // Rotación visual glitchy
                    val t = machete.transformation
                    t.leftRotation.rotateZ(0.6f)
                    machete.setTransformation(t)

                    val hit = machete.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull { !plugin.asesinoManager.esElAsesino(it) }
                    if (hit != null || machete.location.block.type.isSolid) {
                        hit?.let {
                            plugin.combatManager.processTrueDamage(it, player, 5.0)
                            it.playSound(it.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.8f)
                        }
                        break
                    }
                    delay(50); ticks++
                }
                machete.remove(); temporaryEntities.remove(machete)
            }
        }
    }

    // --- 🔊 H3: PRESENCIA ---
    private fun habilidadPresencia(player: Player) {
        player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.8f)
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 100, 1))
            }
        }
    }

    // --- 💀 H4: MODO EJECUCIÓN ---
    private fun habilidadEjecucion(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 300, 3))
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 300, 2))
        dibujarEstrella(player, Color.MAROON, 2.5, 5)

        scope.launch {
            delay(15000)
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 2))
                }
            }
        }
    }

    // --- 🛠️ EQUIPAMIENTO (SISTEMA MULTI-IDIOMA) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()
        val langAsesinos = plugin.messageConfig.getSpecificFile(player, "asesinos")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return
            val namePath = if (key == "arma") "asesinos.slasher.items.arma_nombre" else "asesinos.slasher.items.${key}_nombre"
            langAsesinos.getString(namePath)?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }

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

    // --- 🚀 TRAIL ASÍNCRONO ---
    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val pos = Vector3d(player.location.x, player.location.y + 1.2, player.location.z)
        val blood = WrapperPlayServerParticle(Particle(ParticleTypes.DUST, ParticleDustData(1f, 0f, 0f, 0.8f)), false, pos, Vector3f(0.1f, 0.2f, 0.1f), 0.02f, 1)
        player.world.players.forEach { PacketEvents.getAPI().playerManager.sendPacket(it, blood) }
    }

    override fun mostrarTrailFisico(player: Player) {}

    private fun dibujarEstrella(player: Player, color: Color, radio: Double, puntas: Int) {
        val loc = player.location.add(0.0, 0.1, 0.0)
        val dust = org.bukkit.Particle.DustOptions(color, 1.0f)
        for (i in 0 until puntas) {
            val a = i * Math.PI * 2 / puntas
            val na = (i + 2) * Math.PI * 2 / puntas
            val p1 = loc.clone().add(cos(a) * radio, 0.0, sin(a) * radio)
            val p2 = loc.clone().add(cos(na) * radio, 0.0, sin(na) * radio)
            val dir = p2.toVector().subtract(p1.toVector())
            val len = dir.length(); dir.normalize()
            var d = 0.0
            while (d < len) {
                player.world.spawnParticle(org.bukkit.Particle.DUST, p1.clone().add(dir.clone().multiply(d)), 1, dust)
                d += 0.3
            }
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        temporaryEntities.forEach { it.remove() }
        temporaryEntities.clear()
        scope.coroutineContext.cancelChildren()
    }
}
