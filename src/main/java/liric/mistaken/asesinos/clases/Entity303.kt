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
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * Entity 303: El Hacker de la Realidad.
 * FIX: Slots 1-4, Carga de armadura blindada, Multi-Idioma y True Damage.
 */
class Entity303 : Asesino(
    "entity303",
    Mistaken.Companion.instance.messageConfig.getSpecificFile(null, "asesinos").getString("asesinos.entity303.nombre", "Entity 303")!!
) {

    private val path = "asesinos.entity303"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Bloques representativos del glitch (Redstone y Magma)
    private val orbitMaterials = listOf(Material.REDSTONE_BLOCK, Material.MAGMA_BLOCK, Material.OBSERVER)

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinosConfig(null) // Raíz
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("asesinos.entity303.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("asesinos.entity303.items.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // Mapeo desplazado: Teclas 2, 3, 4, 5 (Slots 1, 2, 3, 4)
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadDashCodigo(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadInfeccionSistema(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadProtocoloVuelo(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadCrashPantalla(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 💻 H1: DASH DE CÓDIGO (CON DAÑO Y KB) ---
    private fun habilidadDashCodigo(player: Player) {
        val dir = player.location.direction.normalize().multiply(2.0).setY(0.2)
        player.velocity = dir

        // Loop de detección durante el dash
        val task = object : BukkitRunnable() {
            var count = 0
            val hitted = mutableSetOf<UUID>()
            override fun run() {
                if (count >= 10 || !player.isOnline) { cancel(); return }
                player.getNearbyEntities(2.0, 2.0, 2.0).filterIsInstance<Player>().forEach { victim ->
                    if (!plugin.asesinoManager.esElAsesino(victim) && !hitted.contains(victim.uniqueId)) {
                        hitted.add(victim.uniqueId)
                        plugin.combatManager.processTrueDamage(victim, player, 5.0) // 2.5 corazones
                        victim.velocity = player.location.direction.normalize().multiply(1.5).setY(0.4)
                        victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                    }
                }
                count++
            }
        }
        task.runTaskTimer(plugin, 0L, 1L)
        player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 1f, 1.5f)
    }

    // --- ☣️ H2: INFECCIÓN DE SISTEMA (True Damage) ---
    private fun habilidadInfeccionSistema(player: Player) {
        val star = player.world.spawn(player.eyeLocation, ItemDisplay::class.java) {
            it.setItemStack(ItemStack(Material.NETHER_STAR))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
        }
        val direction = player.location.direction.multiply(1.5)

        scope.launch {
            var ticks = 0
            withContext(plugin.bukkitDispatcher) {
                while (ticks < 40 && star.isValid) {
                    star.teleport(star.location.add(direction))
                    star.world.spawnParticle(org.bukkit.Particle.ENCHANT, star.location, 5, 0.1, 0.1, 0.1, 0.05)

                    val hit = star.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull { !plugin.asesinoManager.esElAsesino(it) }
                    if (hit != null || star.location.block.type.isSolid) {
                        star.world.spawnParticle(org.bukkit.Particle.EXPLOSION, star.location, 1)
                        hit?.let {
                            plugin.combatManager.processTrueDamage(it, player, 4.0)
                            it.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 2))
                            it.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 100, 1))
                        }
                        break
                    }
                    delay(50); ticks++
                }
                star.remove()
            }
        }
    }

    // --- ✈️ H3: PROTOCOLO DE VUELO ---
    private fun habilidadProtocoloVuelo(player: Player) {
        player.allowFlight = true
        player.isFlying = true
        player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)

        scope.launch {
            delay(5000) // 5 segundos de vuelo
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline && player.gameMode != GameMode.SPECTATOR) {
                    player.isFlying = false
                    player.allowFlight = false
                    player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0))
                    player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f)
                }
            }
        }
    }

    // --- ❌ H4: CRASH DE PANTALLA ---
    private fun habilidadCrashPantalla(player: Player) {
        Bukkit.getOnlinePlayers().forEach { online ->
            if (!plugin.asesinoManager.esElAsesino(online) && online.location.distanceSquared(player.location) < 1600) {
                online.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
                online.playSound(online.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.1f)
                online.sendMessage(mm.deserialize("<red><bold>SYSTEM ERROR: 303 FOUND"))
            }
        }
    }

    // --- 🛠️ EQUIPAMIENTO (MULTI-IDIOMA) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        val lang = plugin.messageConfig.getSpecificFile(player, "asesinos")

        fun giveItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return
            val namePath = if (key == "arma") "asesinos.entity303.items.arma_nombre" else "asesinos.entity303.items.${key}_nombre"
            lang.getString(namePath)?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }

            if (isArmor) {
                when(key) {
                    "casco" -> inv.helmet = item
                    "pechera" -> inv.chestplate = item
                    "pantalones" -> inv.leggings = item
                    "botas" -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        listOf("casco", "pechera", "pantalones", "botas").forEach { giveItem(0, it, true) }
        giveItem(1, "habilidad1")
        giveItem(2, "habilidad2")
        giveItem(3, "habilidad3")
        giveItem(4, "habilidad4")
        giveItem(8, "arma")

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // --- 🧊 MOTOR FÍSICO: GLITCH BLOCKS ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiar(uuid); return }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiar(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            orbitMaterials.map { mat ->
                player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                    bd.block = mat.createBlockData()
                    bd.transformation = Transformation(JomlVector3f(-0.1f, -0.1f, -0.1f), Quaternionf(), JomlVector3f(0.25f, 0.25f, 0.25f), Quaternionf())
                    bd.teleportDuration = 2; bd.interpolationDuration = 2
                }
            }.toMutableList()
        }

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.15) % (Math.PI * 2)
        val radio = 1.3
        for (i in entidades.indices) {
            val offset = (2 * Math.PI / entidades.size) * i
            val x = radio * cos(angulo + offset)
            val z = radio * sin(angulo + offset)
            val y = 1.2 + (0.2 * sin((angulo + offset) * 2))
            entidades[i].teleport(player.location.clone().add(x, y, z))
        }
        angulos[uuid] = angulo
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 1.1, 0.0)
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.DUST, ParticleDustData(1f, 0f, 0f, 0.8f)), false, Vector3d(loc.x, loc.y, loc.z), Vector3f(0.2f, 0.2f, 0.2f), 0.01f, 1)
        loc.world.players.forEach { if (it.location.distanceSquared(loc) < 400.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet) }
    }

    private fun limpiar(uuid: UUID) { orbitadores.remove(uuid)?.forEach { it.remove() }; angulos.remove(uuid) }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiar(it.uniqueId)
            it.allowFlight = false
            it.isFlying = false
        }
        scope.coroutineContext.cancelChildren()
    }
}
