package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.mainThread
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.*
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mariachi v2.1 - Kotlin Jalisco Edition.
 * FIX: Slots de habilidades corregidos (0-3) y carga de armadura blindada.
 * NUEVO: Guitarra física orbitando en la espalda.
 */
class Mariachi : Asesino(
    "mariachi",
    Mistaken.instance.configManager.getAsesinos().getString(
        "asesinos.mariachi.nombre",
        "<gradient:#ff0000:#000000><b>MARIACHI MUERTE</b></gradient>"
    ) ?: "Mariachi"
) {

    private val path = "asesinos.mariachi"
    private val sonidoMúsicaId = "mistaken:jarabetapatio"

    // Caché de items para 0% Spark en equipamiento
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val musicTasks = ConcurrentHashMap<UUID, BukkitRunnable>()

    // --- 🎸 VISUAL: GUITARRA ORBITANTE ---
    private val guitarras = ConcurrentHashMap<UUID, ItemDisplay>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armorKeys.forEach { key ->
            config.getString("$path.armadura.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[key] = it }
                }
            }
        }

        itemKeys.forEach { key ->
            config.getString("$path.items.$key")?.let { id ->
                val name = config.getString("$path.items.${key}_nombre")
                if (id != "none" && id.isNotEmpty()) {
                    CraftEngineUtils.getCustomItem(id)?.let { item ->
                        name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                        itemKitCache[key] = item
                    }
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // 🔥 FIX: Mapeo de slots 0, 1, 2, 3 para las habilidades del YAML
        when (slot) {
            1 -> {
                if (checkCooldown(player, 1)) return
                habilidadGrito(player)
                reproducirEfectosHabilidad(player, 1)
            }
            2 -> {
                if (checkCooldown(player, 2)) return
                habilidadJarabe(player)
                reproducirEfectosHabilidad(player, 2)
            }
            3 -> {
                if (checkCooldown(player, 3)) return
                habilidadGuitarrazo(player)
                reproducirEfectosHabilidad(player, 3)
            }
            4 -> {
                if (checkCooldown(player, 4)) return
                habilidadTequila(player)
                reproducirEfectosHabilidad(player, 4)
            }
        }
    }

    // --- 🚀 TRAIL ASÍNCRONO ---

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return

        val loc = player.location.add(0.0, 0.2, 0.0)
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val mgr = PacketEvents.getAPI().playerManager

        val flamePacket = WrapperPlayServerParticle(Particle(ParticleTypes.SOUL_FIRE_FLAME), false, pos, Vector3f(0.15f, 0.1f, 0.15f), 0.02f, 1)

        loc.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(loc) < 625.0) {
                mgr.sendPacket(p, flamePacket)
                if (ThreadLocalRandom.current().nextFloat() < 0.15f) {
                    mgr.sendPacket(p, WrapperPlayServerParticle(Particle(ParticleTypes.NOTE), false, pos.add(0.0, 1.8, 0.0), Vector3f(0.1f, 0.1f, 0.1f), 0.5f, 1))
                }
            }
        }
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }

        // Fix cambio de mundo
        if (guitarras[uuid]?.world != player.world) limpiarVisuales(uuid)

        val display = guitarras.getOrPut(uuid) {
            player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                id.setItemStack(itemKitCache["arma"] ?: ItemStack(Material.GOLDEN_AXE))
                id.transformation = Transformation(
                    JomlVector3f(0f, 0f, 0f),
                    Quaternionf().rotateY(Math.toRadians(180.0).toFloat()),
                    JomlVector3f(0.8f, 0.8f, 0.8f),
                    Quaternionf()
                )
                id.teleportDuration = 2
                id.interpolationDuration = 2
            }
        }

        // Posicionar la guitarra en la espalda de forma fluida
        val offset = player.location.direction.clone().multiply(-0.4)
        val loc = player.location.clone().add(offset).add(0.0, 1.1, 0.0)
        // Que la guitarra rote un poco con el movimiento
        loc.yaw = player.location.yaw + 180f
        display.teleport(loc)
    }

    // --- 🎸 HABILIDADES ---

    private fun habilidadGrito(player: Player) {
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 1))
                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 2))
                target.sendMessage(mm.deserialize("<red>¡Ese grito te dejó sordo!</red>"))
                target.playSound(target.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.8f)
            }
        }
    }

    private fun habilidadJarabe(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 3))
        player.sendMessage(mm.deserialize("<gold>¡A zapatear se ha dicho!</gold>"))
    }

    private fun habilidadGuitarrazo(player: Player) {
        player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                val v = target.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5)
                v.y = 0.4
                target.velocity = v
                plugin.combatManager.processTrueDamage(target, player, 5.0)
                target.playSound(target.location, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f)
            }
        }
    }

    private fun habilidadTequila(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 120, 4))
        player.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 160, 0))
        player.sendMessage(mm.deserialize("<green>¡Salud! Nada te hace daño ahora.</green>"))
        player.playSound(player.location, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f)
    }

    // --- 🛠️ EQUIPAMIENTO (FIXED) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // 🔥 FIX: Lazy load para asegurar armadura de CraftEngine
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) {
            preLoadKit()
        }

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
        iniciarMusica(player)
    }

    private fun iniciarMusica(player: Player) {
        val uuid = player.uniqueId
        detenerMusica(uuid)

        val runnable = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !plugin.asesinoManager.esElAsesino(player)) {
                    detenerMusica(uuid); cancel(); return
                }
                val loc = player.location
                loc.world.players.forEach { p ->
                    if (p.location.distanceSquared(loc) < 1600) {
                        p.stopSound(sonidoMúsicaId, SoundCategory.RECORDS)
                        p.playSound(loc, sonidoMúsicaId, SoundCategory.RECORDS, 1.5f, 1.0f)
                    }
                }
            }
        }
        val task = runnable.runTaskTimer(plugin, 0L, 2760L)
        musicTasks[uuid] = runnable
        trackTask(task)
    }

    private fun limpiarVisuales(uuid: UUID) {
        guitarras.remove(uuid)?.remove()
        musicTasks.remove(uuid)?.cancel()
    }

    private fun detenerMusica(uuid: UUID) {
        musicTasks.remove(uuid)?.cancel()
        Bukkit.getOnlinePlayers().forEach { it.stopSound(sonidoMúsicaId, SoundCategory.RECORDS) }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarVisuales(it.uniqueId) }
    }
}
