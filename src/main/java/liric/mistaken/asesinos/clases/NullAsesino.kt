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
import liric.mistaken.utils.mainThread
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

/**
 * [LIRIC-MISTAKEN 2.0]
 * NullAsesino: El Ente del Glitch.
 * MEJORAS: Órbita de Items (Faro, Ojo, Estrella), Slots 0-3 y True Damage.
 */
class NullAsesino : Asesino(
    "null",
    Mistaken.instance.configManager.getAsesinos().getString("asesinos.null.nombre", "<dark_gray><b>NULL</b>")!!
) {

    private val pathBase = "asesinos.null"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTraps = ConcurrentHashMap.newKeySet<Entity>()

    // --- 🧊 OBJETOS MÍSTICOS ORBITANTES ---
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val orbitMaterials = listOf(Material.BEACON, Material.ENDER_EYE, Material.NETHER_STAR)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k -> config.getString("$pathBase.armadura.$k")?.let { id -> CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[k] = it } } }
        items.forEach { k ->
            config.getString("$pathBase.items.$k")?.let { id ->
                val name = config.getString("$pathBase.items.${k}_nombre")
                CraftEngineUtils.getCustomItem(id)?.let { item ->
                    name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // 🔥 FIX SLOTS: 0=H1, 1=H2, 2=H3, 3=H4
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadErrorRender(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadGeneradorBait(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadPrisionVacio(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadColmillosVacio(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🏃 HABILIDADES ---

    private fun habilidadErrorRender(player: Player) {
        player.world.spawnParticle(org.bukkit.Particle.FLASH, player.location.add(0.0, 1.0, 0.0), 3, 0.5, 0.5, 0.5, 0.0)
        player.getNearbyEntities(12.0, 12.0, 12.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 200, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
                victim.sendMessage(mm.deserialize("<dark_gray><obfuscated>ERR</obfuscated> <white><b>SISTEMA CORRUPTO</b>"))
                victim.playSound(victim.location, Sound.BLOCK_CONDUIT_DEACTIVATE, 1f, 0.1f)
            }
        }
    }

    private fun habilidadGeneradorBait(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.1, 0.5)
        val bait = loc.world?.spawn(loc, ArmorStand::class.java) { asEntity ->
            asEntity.isVisible = false
            asEntity.isMarker = true
            asEntity.equipment.helmet = ItemStack(Material.BEACON)
        } ?: return
        activeTraps.add(bait)
    }

    private fun habilidadPrisionVacio(player: Player) {
        val ray = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, 15.0) {
            it is Player && !plugin.asesinoManager.esElAsesino(it)
        }
        val victim = ray?.hitEntity as? Player ?: return

        // Succión hacia NULL
        val pull = player.location.toVector().subtract(victim.location.toVector()).normalize().multiply(0.6).setY(0.2)
        victim.velocity = pull

        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 10))
        victim.playSound(victim.location, Sound.BLOCK_CHAIN_PLACE, 1f, 0.5f)
    }

    private fun habilidadColmillosVacio(player: Player) {
        val startLoc = player.location
        val direction = startLoc.direction.setY(0.0).normalize()

        scope.launch {
            val currentLoc = startLoc.clone()
            repeat(15) {
                if (!player.isOnline) return@launch
                withContext(plugin.mainThread) {
                    currentLoc.add(direction)
                    currentLoc.world?.spawn(currentLoc, EvokerFangs::class.java)

                    currentLoc.world?.getNearbyEntities(currentLoc, 1.5, 1.5, 1.5)?.filterIsInstance<Player>()?.forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            plugin.combatManager.processTrueDamage(victim, player, 4.0) // 2 corazones
                            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                        }
                    }
                }
                delay(50)
            }
        }
    }

    // --- 🧊 MOTOR FÍSICO: ÓRBITA DE OBJETOS ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }

        // Fix cambio de mundo
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarVisuales(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf<ItemDisplay>().apply {
                orbitMaterials.forEach { mat -> add(crearObjetoMistico(player.location, mat)) }
            }
        }

        val anguloBase = angulos.getOrDefault(uuid, 0.0)
        val radio = 1.4
        val size = entidades.size

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / size) * i
                val x = radio * cos(anguloBase + offset)
                val z = radio * sin(anguloBase + offset)
                val y = 1.1 + (0.2 * sin((anguloBase + offset) * 2))

                val loc = player.location.clone().add(x, y, z)
                // Que los objetos roten sobre su propio eje también
                loc.yaw = (anguloBase * 50).toFloat()
                display.teleport(loc)
            }
        }
        angulos[uuid] = anguloBase + 0.10
    }

    private fun crearObjetoMistico(loc: Location, mat: Material): ItemDisplay {
        return loc.world.spawn(loc, ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(mat))
            id.transformation = Transformation(
                JomlVector3f(0f, 0f, 0f), Quaternionf(),
                JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf()
            )
            id.teleportDuration = 2
            id.interpolationDuration = 2
        }
    }

    // --- 🚀 VISUALES ---

    override fun mostrarTrail(player: Player) {
        val l = player.location.add(0.0, 1.1, 0.0)
        val mgr = PacketEvents.getAPI().playerManager
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.WITCH), false, Vector3d(l.x, l.y, l.z), Vector3f(0.2f, 0.2f, 0.2f), 0.02f, 1)
        l.world.players.forEach { p -> if (p != player && p.location.distanceSquared(l) < 625.0) mgr.sendPacket(p, packet) }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        itemKitCache["habilidad1"]?.let { inv.setItem(1, it.clone()) }
        itemKitCache["habilidad2"]?.let { inv.setItem(2, it.clone()) }
        itemKitCache["habilidad3"]?.let { inv.setItem(3, it.clone()) }
        itemKitCache["habilidad4"]?.let { inv.setItem(4, it.clone()) }
        itemKitCache["arma"]?.let { inv.setItem(8, it.clone()) }

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    private fun limpiarVisuales(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarVisuales(it.uniqueId) }
        activeTraps.forEach { it.remove() }
        activeTraps.clear()
        scope.coroutineContext.cancelChildren()
    }
}
