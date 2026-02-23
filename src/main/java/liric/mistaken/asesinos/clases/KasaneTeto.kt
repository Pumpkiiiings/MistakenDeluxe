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
 * KasaneTeto: La Quimera de las Baguettes.
 * Optimización: Soporte Multi-Idioma, Fallback Vanilla y Coroutines para timers.
 */
class KasaneTeto : Asesino(
    "teto",
    // Nombre dinámico para la carga inicial (Se actualizará al idioma del jugador en el scoreboard)
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.teto.nombre", "<gradient:#ff66cc:#ff0000><b>KASANE TETO</b></gradient>", "asesinos_info")
) {

    private val path = "asesinos.teto"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    /**
     * 🔥 PRE-LOAD LÓGICO:
     * Carga materiales y IDs del archivo asesinos.yml central (Raíz).
     */
    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$path.armadura.$k")?.let { id ->
                if (id != "none") {
                    // Si no es de CraftEngine, intentamos cargarlo como material de Minecraft
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
        // Mapeo: Teclas 2, 3, 4, 5 -> Slots 1, 2, 3, 4
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadTaladro(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadBaguette(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadBroma(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadTerritory(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🛠️ EQUIPAMIENTO (SISTEMA MULTI-IDIOMA) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        val langAsesinos = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            // Buscamos el nombre traducido en lang/{idioma}/asesinos_info.yml
            val namePath = if (key == "arma") "asesinos.teto.habilidades_nombres.arma"
            else "asesinos.teto.habilidades_nombres.$key"

            val localizedName = langAsesinos.getString(namePath)
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

        // 1. Armadura
        setLocalizedItem(0, "casco", true)
        setLocalizedItem(0, "pechera", true)
        setLocalizedItem(0, "pantalones", true)
        setLocalizedItem(0, "botas", true)

        // 2. Hotbar (Slots 1, 2, 3, 4 y Arma en 8)
        setLocalizedItem(1, "habilidad1")
        setLocalizedItem(2, "habilidad2")
        setLocalizedItem(3, "habilidad3")
        setLocalizedItem(4, "habilidad4")
        setLocalizedItem(8, "arma")

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // --- 🥖 HABILIDADES ---

    private fun habilidadTaladro(player: Player) {
        player.getNearbyEntities(3.5, 3.5, 3.5).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                plugin.gameManager.combatManager.takeDamage(victim)
                victim.addPotionEffect(PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 1))
                victim.playSound(victim.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f)
            }
        }
        player.world.spawnParticle(org.bukkit.Particle.CRIT, player.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.3, 0.3, 0.1)
    }

    private fun habilidadBaguette(player: Player) {
        player.velocity = player.location.direction.multiply(1.1).setY(0.2)
        player.getNearbyEntities(2.5, 2.5, 2.5).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.2).setY(0.3)
            }
        }
    }

    private fun habilidadBroma(player: Player) {
        player.world.spawnParticle(org.bukkit.Particle.EXPLOSION, player.location, 1)
        player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.sendMessage(mm.deserialize("<gradient:#ff66cc:#ff0000><b>¡BAKA!</b> ¿Te la creíste?</gradient>"))
            }
        }
    }

    private fun habilidadTerritory(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 240, 0))
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 240, 1))
        player.isGlowing = true

        // Coroutine en vez de BukkitRunnable para ahorrar recursos
        val job = scope.launch {
            delay(12000) // 240 ticks = 12 segundos
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                    player.isGlowing = false
                    player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f)
                }
            }
        }
        trackJob(job) // Se añade al rastreador automático de limpieza
    }

    // --- 🚀 VISUALES ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarBaguettes(uuid); return }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarBaguettes(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf<ItemDisplay>().apply {
                repeat(4) {
                    add(player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                        id.setItemStack(ItemStack(Material.BREAD))
                        id.transformation = Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf(), JomlVector3f(0.8f, 0.8f, 0.8f), Quaternionf())
                        id.teleportDuration = 1; id.interpolationDuration = 1
                    })
                }
            }
        }

        val anguloBase = angulos.getOrDefault(uuid, 0.0)
        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / entidades.size) * i
                val x = 1.2 * cos(anguloBase + offset)
                val z = 1.2 * sin(anguloBase + offset)
                val y = 1.1 + (0.1 * sin((anguloBase + offset) * 2))

                val loc = player.location.clone().add(x, y, z)
                loc.yaw = ((anguloBase + offset) * 180 / Math.PI).toFloat() + 90f
                display.teleport(loc)
            }
        }
        angulos[uuid] = (anguloBase + 0.12) % (Math.PI * 2)
    }

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val pos = Vector3d(player.location.x, player.location.y + 0.2, player.location.z)
        // Partículas Spore Blossom (Estilo Sakura)
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.SPORE_BLOSSOM_AIR), false, pos, Vector3f(0.3f, 0.1f, 0.3f), 0.01f, 1)

        player.world.players.forEach {
            if (it.location.distanceSquared(player.location) < 625.0) {
                PacketEvents.getAPI().playerManager.sendPacket(it, packet)
            }
        }
    }

    private fun limpiarBaguettes(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarBaguettes(it.uniqueId) }
        scope.coroutineContext.cancelChildren()
    }
}
