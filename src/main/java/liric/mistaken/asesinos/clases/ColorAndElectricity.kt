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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ThreadLocalRandom

/**
 * ColorAndElectricity - Edición Kotlin (Muship Style)
 * Optimizado para Paper 1.21.4
 */
class ColorAndElectricity : Asesino(
    "colorandelectricity",
    Mistaken.instance.configManager.asesinos.getString(
        "asesinos.colorandelectricity.nombre",
        "<gradient:#ff0080:#00ffff:#ffff00><b>色彩電気</b></gradient>"
    )!!
) {

    private val path = "asesinos.colorandelectricity"
    private val palette = arrayOf(
        floatArrayOf(1f, 0f, 0.5f), // Rosa
        floatArrayOf(0f, 1f, 1f),   // Cian
        floatArrayOf(1f, 1f, 0f)    // Amarillo
    )

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return

        // Ejecución inmediata de lógica síncrona
        when (slot) {
            1 -> habilidadVividTrace(player)
            2 -> habilidadColorDrain(player)
            3 -> habilidadPulseStatic(player)
            4 -> habilidadShikisaiEnd(player)
        }

        // Feedback visual del slot
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 🚀 TRAIL DE PARTÍCULAS ASÍNCRONO ---

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 0.1, 0.0)
        val random = ThreadLocalRandom.current()
        val rgb = palette[random.nextInt(palette.size)]

        // Construcción de paquete PacketEvents (Async Safe)
        val dust = Particle(ParticleTypes.DUST).apply {
            data = ParticleDustData(rgb[0], rgb[1], rgb[2], 1.2f)
        }

        val packet = WrapperPlayServerParticle(
            dust, false, Vector3d(loc.x, loc.y, loc.z),
            Vector3f(0.2f, 0.2f, 0.2f), 0.01f, 1
        )

        // Culling inteligente: Solo enviar a jugadores cerca (radio 25)
        val distSq = 625.0
        loc.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(loc) < distSq) {
                PacketEvents.getAPI().playerManager.sendPacket(p, packet)
            }
        }
    }

    override fun mostrarTrailFisico(player: Player) {
        // Optimización: No requiere lógica en el hilo principal
    }

    // --- HABILIDADES ---

    private fun habilidadVividTrace(player: Player) {
        // Impulso cinético
        player.velocity = player.location.direction.multiply(1.8).setY(0.2)
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f)
        player.world.spawnParticle(org.bukkit.Particle.WITCH, player.location, 15, 0.3, 0.3, 0.3, 0.1)
    }

    private fun habilidadColorDrain(player: Player) {
        player.playSound(player.location, Sound.BLOCK_CONDUIT_ATTACK_TARGET, 1f, 1.8f)

        // Buscamos víctimas en el hilo principal
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                victim.sendMessage(mm.deserialize("<gradient:#ff0080:#00ffff><i>\"Dame tus colores...\"</i></gradient>"))
            }
        }
    }

    private fun habilidadPulseStatic(player: Player) {
        // Usamos el scope del plugin para una tarea temporal
        plugin.lifecycleManager.run {
            // Para habilidades con repetición corta, BukkitRunnable sigue siendo útil,
            // pero aquí lo lanzamos limpio.
            var ticks = 0
            val task = object : org.bukkit.scheduler.BukkitRunnable() {
                override fun run() {
                    if (ticks > 3 || !player.isOnline) { cancel(); return }

                    player.world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, player.location, 20, 2.0, 2.0, 2.0, 0.05)
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1.5f + (ticks * 0.2f))

                    player.getNearbyEntities(5.0, 5.0, 5.0).filterIsInstance<Player>().forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            victim.damage(1.0, player)
                            victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 40, 1))
                        }
                    }
                    ticks++
                }
            }
            task.runTaskTimer(plugin, 0L, 5L)
            trackTask(task)
        }
    }

    private fun habilidadShikisaiEnd(player: Player) {
        // Buscar objetivo más cercano (Max 15 bloques)
        val target = player.getNearbyEntities(15.0, 15.0, 15.0)
            .filterIsInstance<Player>()
            .find { !plugin.asesinoManager.esElAsesino(it) }

        target?.let {
            // Teleport Asíncrono de Paper
            player.teleportAsync(it.location).thenAccept { success ->
                if (success) {
                    player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 0.5f)
                    player.world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.location, 30, 0.5, 1.0, 0.5, 0.5)
                    it.sendMessage(mm.deserialize("<red><b>[!] SOBRECARGA CROMÁTICA</b></red>"))
                    it.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 120, 0))
                }
            }
        }
    }

    // --- EQUIPAMIENTO ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        val config = plugin.configManager.asesinos

        // Uso de extensiones de Kotlin para mayor limpieza
        inv.apply {
            helmet = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.casco"))
            chestplate = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.pechera"))
            leggings = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.pantalones"))
            boots = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.botas"))
        }

        // Equipar items de habilidades
        for (i in 0..4) {
            val itemKey = if (i == 0) "arma" else "habilidad$i"
            val id = config.getString("$path.items.$itemKey")
            val name = config.getString("$path.items.${itemKey}_nombre")

            if (id != null && !id.equals("none", ignoreCase = true)) {
                CraftEngineUtils.getCustomItem(id)?.let { item ->
                    name?.let {
                        item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
                    }
                    inv.setItem(if (i == 0) 8 else i, item)
                }
            }
        }

        player.heldItemSlot = 8
        player.updateInventory()
    }
}
