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
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * Mariachi v2.0 - Kotlin Jalisco Edition.
 * Optimización: Caché de items, música con culling de red y Trail Async.
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

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armorKeys.forEach { key ->
            config.getString("$path.armadura.$key")?.let { id ->
                if (id != "none") CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[key] = it }
            }
        }

        itemKeys.forEach { key ->
            config.getString("$path.items.$key")?.let { id ->
                val name = config.getString("$path.items.${key}_nombre")
                if (id != "none") {
                    CraftEngineUtils.getCustomItem(id)?.let { item ->
                        name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                        itemKitCache[key] = item
                    }
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return

        when (slot) {
            1 -> habilidadGrito(player)
            2 -> habilidadJarabe(player)
            3 -> habilidadGuitarrazo(player)
            4 -> habilidadTequila(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 🚀 TRAIL ASÍNCRONO (MOTOR MAESTRO) ---

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return

        val loc = player.location.add(0.0, 0.2, 0.0)
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val off = Vector3f(0.15f, 0.1f, 0.15f)
        val mgr = PacketEvents.getAPI().playerManager

        // Llama de fuego azul (Soul Fire)
        val flamePacket = WrapperPlayServerParticle(
            Particle(ParticleTypes.SOUL_FIRE_FLAME), false, pos, off, 0.02f, 1
        )

        val distSq = 625.0 // 25 bloques
        loc.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(loc) < distSq) {
                mgr.sendPacket(p, flamePacket)

                // Notas musicales aleatorias (15% chance)
                if (ThreadLocalRandom.current().nextFloat() < 0.15f) {
                    mgr.sendPacket(p, WrapperPlayServerParticle(
                        Particle(ParticleTypes.NOTE), false, pos.add(0.0, 1.8, 0.0), off, 0.5f, 1
                    ))
                }
            }
        }
    }

    override fun mostrarTrailFisico(player: Player) {
        // No requiere entidades físicas orbitando
    }

    // --- 🎼 LÓGICA DE MÚSICA ---

    private fun iniciarMusica(player: Player) {
        val uuid = player.uniqueId
        detenerMusica(uuid)

        val runnable = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !plugin.asesinoManager.esElAsesino(player)) {
                    detenerMusica(uuid)
                    cancel()
                    return
                }

                val loc = player.location
                // Culling de red: Solo enviar sonido a los que están cerca (40 bloques)
                loc.world.players.forEach { p ->
                    if (p.location.distanceSquared(loc) < 1600) {
                        p.stopSound(sonidoMúsicaId, SoundCategory.RECORDS)
                        p.playSound(loc, sonidoMúsicaId, SoundCategory.RECORDS, 1.5f, 1.0f)
                    }
                }
            }
        }

        val task = runnable.runTaskTimer(plugin, 0L, 2760L) // Duración del Jarabe Tapatío
        musicTasks[uuid] = runnable
        trackTask(task)
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
        // Zapateado: Velocidad IV
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 3))
        player.sendMessage(mm.deserialize("<gold>¡A zapatear se ha dicho!</gold>"))
    }

    private fun habilidadGuitarrazo(player: Player) {
        player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                val v = target.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5)
                v.y = 0.4
                target.velocity = v

                // Daño Real: 5 HP (2.5 corazones)
                plugin.combatManager.processTrueDamage(target, player, 5.0)
                target.playSound(target.location, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f)
            }
        }
    }

    private fun habilidadTequila(player: Player) {
        // Resistencia V pero con mareo
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 120, 4))
        player.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 160, 0))
        player.sendMessage(mm.deserialize("<green>¡Salud! Nada te hace daño ahora.</green>"))
        player.playSound(player.location, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f)
    }

    // --- EQUIPAMIENTO ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Si el caché falló en el init, reintentamos (Fix para CraftEngine/Oraxen)
        if (!itemKitCache.containsKey("casco")) preLoadKit()

        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        inv.setItem(8, itemKitCache["arma"]?.clone())
        inv.setItem(1, itemKitCache["habilidad1"]?.clone())
        inv.setItem(2, itemKitCache["habilidad2"]?.clone())
        inv.setItem(3, itemKitCache["habilidad3"]?.clone())
        inv.setItem(4, itemKitCache["habilidad4"]?.clone())

        player.inventory.heldItemSlot = 8
        player.updateInventory()

        iniciarMusica(player)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { detenerMusica(it.uniqueId) }
    }

    private fun detenerMusica(uuid: UUID) {
        musicTasks.remove(uuid)?.cancel()
        Bukkit.getOnlinePlayers().forEach { it.stopSound(sonidoMúsicaId, SoundCategory.RECORDS) }
    }
}
