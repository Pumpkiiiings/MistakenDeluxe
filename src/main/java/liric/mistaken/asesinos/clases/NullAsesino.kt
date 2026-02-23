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
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * NullAsesino: El Ente del Glitch.
 * FIX: Fallback de armadura por slot y forEach Null-Safe corregido.
 */
class NullAsesino : Asesino(
    "null",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.null.nombre", "<dark_gray><b>NULL</b>", "asesinos_info")
) {

    private val pathBase = "asesinos.null"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTraps = ConcurrentHashMap.newKeySet<Entity>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Órbita
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val orbitMaterials = listOf(Material.BEACON, Material.ENDER_EYE, Material.NETHER_STAR)

    init {
        preLoadKit()
    }

    /**
     * 🔥 CARGADOR BLINDADO:
     * Asigna un material de respaldo específico por cada slot de armadura.
     */
    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()

        // Mapa de respaldos: Si falla CraftEngine, le damos la pieza correcta de Netherite
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
                // Si falla CraftEngine, intentamos material por nombre, si no, el fallback forzoso
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: fallbackMat)
            }
        }

        // Cargar ítems de habilidad
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
            1 -> if (!checkCooldown(player, 1)) { habilidadErrorRender(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadGeneradorBait(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadPrisionVacio(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadColmillosVacio(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        if (itemKitCache.isEmpty()) preLoadKit()

        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return
            val namePath = if (key == "arma") "asesinos.null.habilidades_nombres.arma"
            else "asesinos.null.habilidades_nombres.$key"

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
            } else {
                inv.setItem(slot, item)
            }
        }

        // Entregar todo el kit traducido
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

    private fun habilidadErrorRender(player: Player) {
        player.world.spawnParticle(org.bukkit.Particle.FLASH, player.location.add(0.0, 1.0, 0.0), 3, 0.5, 0.5, 0.5, 0.0)
        player.world.getNearbyPlayers(player.location, 12.0).forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 200, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
                victim.sendMessage(mm.deserialize("<dark_gray><obfuscated>ERR</obfuscated> <white><b>SISTEMA CORRUPTO</b> <dark_gray><obfuscated>ERR</obfuscated>"))
            }
        }
    }

    private fun habilidadGeneradorBait(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.1, 0.5)
        val bait = loc.world?.spawn(loc, ArmorStand::class.java) { asEntity ->
            asEntity.isVisible = false
            asEntity.setGravity(false)
            asEntity.isMarker = true
            asEntity.equipment.helmet = ItemStack(Material.BEACON)
        } ?: return
        activeTraps.add(bait)

        val job = scope.launch {
            var timer = 0
            while (isActive && timer < 400 && !bait.isDead) {
                withContext(plugin.bukkitDispatcher) {
                    val angle = timer * 0.4
                    val x = cos(angle) * 0.7
                    val z = sin(angle) * 0.7
                    loc.world?.spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(x, 1.0, z), 1, 0.0, 0.0, 0.0, 0.0)

                    // 🔥 FIX: Check de jugadores cercanos corregido
                    val victim = loc.world?.getNearbyPlayers(loc, 3.5)?.firstOrNull {
                        !plugin.asesinoManager.esElAsesino(it)
                    }

                    victim?.let { v ->
                        plugin.gameManager.combatManager.takeDamage(v)
                        v.playSound(v.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.1f)
                        cleanupTrap(bait)
                        cancel()
                    }
                }
                delay(100)
                timer++
            }
            if (isActive) withContext(plugin.bukkitDispatcher) { cleanupTrap(bait) }
        }
        trackJob(job)
    }

    private fun habilidadPrisionVacio(player: Player) {
        val ray = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, 15.0) {
            it is Player && !plugin.asesinoManager.esElAsesino(it)
        }
        val victim = ray?.hitEntity as? Player ?: return
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 10))
        player.playSound(victim.location, Sound.BLOCK_CHAIN_PLACE, 1f, 0.5f)
    }

    private fun habilidadColmillosVacio(player: Player) {
        val startLoc = player.location
        val direction = startLoc.direction.setY(0.0).normalize()
        val job = scope.launch {
            val currentLoc = startLoc.clone()
            repeat(15) {
                if (!isActive) return@launch
                withContext(plugin.bukkitDispatcher) {
                    currentLoc.add(direction)
                    if (!currentLoc.block.type.isSolid) {
                        currentLoc.world?.spawn(currentLoc, EvokerFangs::class.java)

                        // 🔥 EL FIX QUE BUSCABAS: Agregamos el ?. antes del forEach
                        currentLoc.world?.getNearbyPlayers(currentLoc, 1.5)?.forEach { victim ->
                            if (!plugin.asesinoManager.esElAsesino(victim)) {
                                plugin.gameManager.combatManager.takeDamage(victim)
                                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                            }
                        }
                    }
                }
                delay(50)
            }
        }
        trackJob(job)
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarVisuales(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf<ItemDisplay>().apply {
                orbitMaterials.forEach { mat ->
                    add(player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                        id.setItemStack(ItemStack(mat))
                        id.transformation = Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
                        id.teleportDuration = 2; id.interpolationDuration = 2
                    })
                }
            }
        }

        val anguloActual = (angulos.getOrDefault(uuid, 0.0) + 0.10) % (Math.PI * 2)
        val radio = 1.4
        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / entidades.size) * i
                val x = radio * cos(anguloActual + offset)
                val z = radio * sin(anguloActual + offset)
                display.teleport(player.location.clone().add(x, 1.1 + (0.2 * sin(anguloActual * 2)), z))
            }
        }
        angulos[uuid] = anguloActual
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 1.1, 0.0)
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val mgr = PacketEvents.getAPI().playerManager
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.WITCH), false, pos, Vector3f(0.2f, 0.2f, 0.2f), 0.02f, 1)
        loc.world.players.forEach { if (it != player && it.location.distanceSquared(loc) < 625.0) mgr.sendPacket(it, packet) }
    }

    private fun cleanupTrap(trap: Entity) { trap.remove(); activeTraps.remove(trap) }
    private fun limpiarVisuales(uuid: UUID) { orbitadores.remove(uuid)?.forEach { it.remove() }; angulos.remove(uuid) }
    override fun cleanup(player: Player?) { super.cleanup(player); player?.let { limpiarVisuales(it.uniqueId) }; activeTraps.forEach { it.remove() }; activeTraps.clear(); scope.coroutineContext.cancelChildren() }
}
