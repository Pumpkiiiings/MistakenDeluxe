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
 * FIX: Sistema de equipamiento híbrido y optimización de habilidades con Coroutines.
 */
class KasaneTeto : Asesino(
    "teto",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.teto.nombre", "<gradient:#ff66cc:#ff0000><b>KASANE TETO</b></gradient>", "asesinos_info")
) {

    private val pathBase = "asesinos.teto"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    /**
     * 🔥 PRE-LOAD (Mecánicas):
     * Carga materiales desde el asesinos.yml global (la raíz).
     */
    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$pathBase.armadura.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.NETHERITE_HELMET)
                }
            }
        }

        items.forEach { k ->
            config.getString("$pathBase.items.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.PAPER)
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

    /**
     * 🛠️ EQUIPAMIENTO (Visuales):
     * Pone los trapos y las baguettes con nombres traducidos según el jugador.
     */
    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4) // Fix de armadura para Kotlin

        if (itemKitCache.isEmpty()) preLoadKit()

        // Jalamos el archivo de traducción (asesinos_info.yml)
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            // Ruta de nombres en el nuevo YAML de información
            val namePath = if (key == "arma") "asesinos.teto.habilidades_nombres.arma"
            else "asesinos.teto.habilidades_nombres.$key"

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

        // Entregar el kit completo
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

    // --- 🥖 HABILIDADES ---

    private fun habilidadTaladro(player: Player) {
        // Usamos getNearbyPlayers para no laguear buscando vacas o ítems
        player.world.getNearbyPlayers(player.location, 3.5).forEach { victim ->
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
        player.world.getNearbyPlayers(player.location, 2.5).forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.2).setY(0.3)
            }
        }
    }

    private fun habilidadBroma(player: Player) {
        player.world.spawnParticle(org.bukkit.Particle.EXPLOSION, player.location, 1)
        player.world.getNearbyPlayers(player.location, 6.0).forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                // Mensaje de broma (Podrías moverlo al messages.yml si quieres, carnal)
                victim.sendMessage(mm.deserialize("<gradient:#ff66cc:#ff0000><b>¡BAKA!</b> ¿Te la creíste?</gradient>"))
            }
        }
    }

    private fun habilidadTerritory(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 240, 0))
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 240, 1))
        player.isGlowing = true

        val job = scope.launch {
            delay(12000) // 12 segundos
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                    player.isGlowing = false
                    player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f)
                }
            }
        }
        trackJob(job)
    }

    // --- 🚀 VISUALES (Baguettes Orbitantes) ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarBaguettes(uuid); return }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarVisuales(uuid)

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

        val anguloActual = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / entidades.size) * i
                val x = 1.2 * cos(anguloActual + offset)
                val z = 1.2 * sin(anguloActual + offset)
                val y = 1.1 + (0.1 * sin((anguloActual + offset) * 2))

                val loc = player.location.clone().add(x, y, z)
                loc.yaw = ((anguloActual + offset) * 180 / Math.PI).toFloat() + 90f
                display.teleport(loc)
            }
        }
        angulos[uuid] = anguloActual
    }

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val pos = Vector3d(player.location.x, player.location.y + 0.2, player.location.z)
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.SPORE_BLOSSOM_AIR), false, pos, Vector3f(0.3f, 0.1f, 0.3f), 0.01f, 1)

        player.world.players.forEach { viewer ->
            if (viewer != player && viewer.location.distanceSquared(player.location) < 625.0) {
                PacketEvents.getAPI().playerManager.sendPacket(viewer, packet)
            }
        }
    }

    private fun limpiarBaguettes(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    private fun limpiarVisuales(uuid: UUID) {
        limpiarBaguettes(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarBaguettes(it.uniqueId) }
        scope.coroutineContext.cancelChildren()
    }
}
