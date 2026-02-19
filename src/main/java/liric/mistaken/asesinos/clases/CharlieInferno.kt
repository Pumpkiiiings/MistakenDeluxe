package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * CharlieInferno - Edición Kotlin Ultra-Optimizada
 *
 * Mejoras:
 * - Uso de BlockDisplays para rotación suave (Interpolación).
 * - Manejo de tareas integrado con la clase base Asesino.
 * - Soporte para AdvancedSlimePaper (Detección de cambio de mundo).
 */
class CharlieInferno : Asesino(
    "charlie",
    Mistaken.instance.configManager.getAsesinos()
        .getString("asesinos.charlie.nombre", "<gradient:#ff4500:#ff8c00><b>CHARLIE INFERNO</b></gradient>")!!
) {

    private val path = "asesinos.charlie"
    private val sonidoId = "mistaken:charlieinferno"

    // Mapas concurrentes para evitar errores de hilos
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val musicTasks = ConcurrentHashMap<UUID, BukkitRunnable>()

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        reproducirEfectosHabilidad(player, slot)

        when (slot) {
            1 -> habilidadColdShower(player)
            // Aquí puedes agregar el resto de habilidades (2, 3, 4)
        }
    }

    /**
     * TRAIL ASÍNCRONO: Partículas (PacketEvents)
     * Se ejecuta fuera del hilo principal para máximo rendimiento.
     */
    override fun mostrarTrail(player: Player) {
        if (!player.isValid) return

        val loc = player.location
        val packet = WrapperPlayServerParticle(
            Particle(ParticleTypes.FLAME),
            false,
            Vector3d(loc.x, loc.y + 1.0, loc.z),
            Vector3f(0.15f, 0.15f, 0.15f),
            0.02f,
            2
        )

        val distSq = 400.0 // 20 bloques de radio
        loc.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(loc) < distSq) {
                PacketEvents.getAPI().playerManager.sendPacket(p, packet)
            }
        }
    }

    /**
     * TRAIL FÍSICO: Bloques de cuarzo orbitando.
     * Invocado por el scheduler principal.
     */
    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        val state = plugin.gameManager.currentState

        // Lógica de visibilidad (Lobby vs In-Game)
        val esLobby = state == GameState.LOBBY || state == GameState.VOTING || state == GameState.STARTING
        val selectedKiller = plugin.playerDataManager.getSelectedKiller(uuid)

        val debeSeguir = if (esLobby) {
            selectedKiller.equals("charlie", ignoreCase = true)
        } else {
            plugin.asesinoManager.esElAsesino(player)
        }

        if (!debeSeguir) {
            limpiarEntidades(uuid)
            return
        }

        // --- FIX: Detección de salto de mundo (Teleport a Arena) ---
        val actuales = orbitadores[uuid]
        if (actuales != null && actuales.isNotEmpty()) {
            if (actuales[0].world != player.world) {
                limpiarEntidades(uuid)
            }
        }

        // Crear entidades si no existen
        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf(
                crearBloqueOrbitante(player.location, Material.QUARTZ_BLOCK),
                crearBloqueOrbitante(player.location, Material.QUARTZ_BLOCK)
            )
        }

        // Calcular órbitas
        val angulo = angulos.getOrDefault(uuid, 0.0)
        val radius = 1.2
        val x = radius * Math.cos(angulo)
        val z = radius * Math.sin(angulo)

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val height = if (i == 0) 1.8 else 1.0
                val sideMult = if (i == 0) 1.0 else -1.0

                // Teleport con interpolación suave de la 1.21.4
                display.teleport(player.location.clone().add(x * sideMult, height, z * sideMult))
            }
        }

        angulos[uuid] = angulo + 0.15
    }

    private fun crearBloqueOrbitante(loc: Location, mat: Material): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                JomlVector3f(0f, 0f, 0f),
                Quaternionf(),
                JomlVector3f(0.45f, 0.45f, 0.45f), // Un poco más pequeño para estética
                Quaternionf()
            )
            bd.teleportDuration = 2 // Suavizado de red
            bd.interpolationDuration = 2
        }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        val config = plugin.configManager.getAsesinos()

        // Carga de armadura
        inv.apply {
            helmet = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.casco", "NETHERITE_HELMET"))
            chestplate = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.pechera", "LEATHER_CHESTPLATE"))
            leggings = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.pantalones", "LEATHER_LEGGINGS"))
            boots = CraftEngineUtils.getCustomItem(config.getString("$path.armadura.botas", "LEATHER_BOOTS"))
        }

        // Carga de ítems de habilidad
        for (i in 0..4) {
            val itemKey = if (i == 0) "arma" else "habilidad$i"
            val id = config.getString("$path.items.$itemKey")
            val customName = config.getString("$path.items.${itemKey}_nombre")

            if (id != null && !id.equals("none", ignoreCase = true)) {
                CraftEngineUtils.getCustomItem(id)?.let { item ->
                    if (!customName.isNullOrEmpty()) {
                        item.editMeta { it.displayName(mm.deserialize(customName)) }
                    }
                    inv.setItem(if (i == 0) 8 else i, item)
                }
            }
        }

        inv.heldItemSlot = 8
        player.updateInventory()
        iniciarMusicaCharlie(player)
    }

    private fun iniciarMusicaCharlie(player: Player) {
        val uuid = player.uniqueId
        if (musicTasks.containsKey(uuid)) return

        val runnable = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !plugin.asesinoManager.esElAsesino(player)) {
                    detenerMusica(uuid)
                    cancel()
                    return
                }

                val loc = player.location
                loc.world.players.forEach { p ->
                    p.stopSound(sonidoId, SoundCategory.RECORDS)
                    p.playSound(loc, sonidoId, SoundCategory.RECORDS, 2.5f, 1.0f)
                }
            }
        }

        // CORRECCIÓN: Guardamos la BukkitTask devuelta por runTaskTimer
        val task = runnable.runTaskTimer(plugin, 0L, 1480L)
        musicTasks[uuid] = runnable

        // Registramos en la clase base para que se limpie sola al terminar
        trackTask(task)
    }

    private fun habilidadColdShower(player: Player) {
        player.getNearbyEntities(7.0, 7.0, 7.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 2))
                target.freezeTicks = 120 // 6 segundos de congelamiento
            }
        }
    }

    private fun detenerMusica(uuid: UUID) {
        musicTasks.remove(uuid)?.cancel()
        Bukkit.getOnlinePlayers().forEach { it.stopSound(sonidoId, SoundCategory.RECORDS) }
    }

    private fun limpiarEntidades(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        // Ejecuta la limpieza de la clase base (cooldowns, atributos, jobs, tasks)
        super.cleanup(player)

        // Limpieza específica de Charlie
        player?.let {
            limpiarEntidades(it.uniqueId)
            detenerMusica(it.uniqueId)
        }
    }
}
