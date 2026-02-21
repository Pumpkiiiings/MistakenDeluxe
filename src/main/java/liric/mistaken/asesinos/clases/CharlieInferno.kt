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
import liric.mistaken.utils.mainThread
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
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

/**
 * CharlieInferno - El Maestro de la Temperatura v2.0
 * Sincronizado con YAML y optimizado con Caché de Items y BlockDisplays.
 */
class CharlieInferno : Asesino(
    "charlie",
    Mistaken.Companion.instance.configManager.getAsesinos()
        .getString("asesinos.charlie.nombre", "<gradient:#ff4500:#ff8c00><b>CHARLIE INFERNO</b></gradient>")!!
) {

    private val path = "asesinos.charlie"
    private val sonidoId = "mistaken:charlieinferno"

    // --- 🚀 OPTIMIZACIÓN: CACHÉ DE KIT ---
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // --- 🧊 ELEMENTAL ORBITERS (Fuego y Hielo) ---
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val musicTasks = ConcurrentHashMap<UUID, BukkitRunnable>()

    private val orbitMaterials = listOf(Material.MAGMA_BLOCK, Material.PACKED_ICE)

    init {
        preLoadKit()
    }

    /**
     * Carga todos los items y nombres del YAML en el arranque.
     */
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
            1 -> habilidadColdShower(player)
            2 -> habilidadThePledge(player)
            3 -> habilidadGoldenEscalator(player)
            4 -> habilidadExhaustion(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- ❄️ HABILIDADES SINCRONIZADAS ---

    private fun habilidadColdShower(player: Player) {
        player.getNearbyEntities(7.0, 7.0, 7.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 2))
                target.freezeTicks = 120
                target.playSound(target.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
            }
        }
    }

    private fun habilidadThePledge(player: Player) {
        val direction = player.location.direction.multiply(1.5).setY(0.2)
        player.velocity = direction
        player.world.spawnParticle(org.bukkit.Particle.FLAME, player.location, 30, 0.5, 0.5, 0.5, 0.1)
    }

    private fun habilidadGoldenEscalator(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 2))
        player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1))

        val loc = player.location
        for (i in 0..20) {
            val angle = i * 0.5
            val x = 0.8 * Math.cos(angle)
            val z = 0.8 * Math.sin(angle)
            loc.world.spawnParticle(org.bukkit.Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, loc.clone().add(x, i * 0.1, z), 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun habilidadExhaustion(player: Player) {
        player.getNearbyEntities(12.0, 12.0, 12.0).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 200, 1))
                target.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                target.sendMessage(mm.deserialize("<red><b>¡ Charlie te ha dejado exhausto !</b></red>"))
            }
        }
    }

    // --- 🚀 VISUALES (TRAILS Y ÓRBITAS) ---

    override fun mostrarTrail(player: Player) {
        if (!player.isValid) return
        val loc = player.location
        val packet = WrapperPlayServerParticle(
            Particle(ParticleTypes.FLAME), false,
            Vector3d(loc.x, loc.y + 1.0, loc.z),
            Vector3f(0.15f, 0.15f, 0.15f), 0.02f, 2
        )
        loc.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(loc) < 400.0) {
                PacketEvents.getAPI().playerManager.sendPacket(p, packet)
            }
        }
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        val state = plugin.gameManager.currentState
        val esLobby = state == GameState.LOBBY || state == GameState.VOTING || state == GameState.STARTING

        val selectedKiller = plugin.playerDataManager.getSelectedKiller(uuid)
        val debeSeguir = if (esLobby) selectedKiller.equals("charlie", true) else plugin.asesinoManager.esElAsesino(player)

        if (!debeSeguir) { limpiarEntidades(uuid); return }

        // Fix cambio de mundo
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarEntidades(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            orbitMaterials.map { mat -> crearBloqueOrbitante(player.location, mat) }.toMutableList()
        }

        val angulo = angulos.getOrDefault(uuid, 0.0)
        val radius = 1.3

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = if (i == 0) 0.0 else Math.PI
                val x = radius * Math.cos(angulo + offset)
                val z = radius * Math.sin(angulo + offset)
                val y = if (i == 0) 1.8 else 0.8 // Una arriba (fuego) y una abajo (hielo)

                display.teleport(player.location.clone().add(x, y, z))
            }
        }
        angulos[uuid] = angulo + 0.15
    }

    private fun crearBloqueOrbitante(loc: Location, mat: Material): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(),
                JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf()
            )
            bd.teleportDuration = 2; bd.interpolationDuration = 2
        }
    }

    // --- 🛠️ EQUIPAMIENTO ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Lazy load fix
        if (itemKitCache.isEmpty()) preLoadKit()

        inv.helmet = itemKitCache["casco"]?.clone()
        inv.chestplate = itemKitCache["pechera"]?.clone()
        inv.leggings = itemKitCache["pantalones"]?.clone()
        inv.boots = itemKitCache["botas"]?.clone()

        inv.setItem(8, itemKitCache["arma"]?.clone())
        inv.setItem(0, itemKitCache["habilidad1"]?.clone())
        inv.setItem(1, itemKitCache["habilidad2"]?.clone())
        inv.setItem(2, itemKitCache["habilidad3"]?.clone())
        inv.setItem(3, itemKitCache["habilidad4"]?.clone())

        player.inventory.heldItemSlot = 8
        player.updateInventory()
        iniciarMusicaCharlie(player)
    }

    private fun iniciarMusicaCharlie(player: Player) {
        val uuid = player.uniqueId
        if (musicTasks.containsKey(uuid)) return
        val runnable = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !plugin.asesinoManager.esElAsesino(player)) {
                    detenerMusica(uuid); cancel(); return
                }
                val loc = player.location
                loc.world.players.forEach { p ->
                    if (p.location.distanceSquared(loc) < 1600) {
                        p.stopSound(sonidoId, SoundCategory.RECORDS)
                        p.playSound(loc, sonidoId, SoundCategory.RECORDS, 2.0f, 1.0f)
                    }
                }
            }
        }
        val task = runnable.runTaskTimer(plugin, 0L, 1480L)
        musicTasks[uuid] = runnable
        trackTask(task)
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
        super.cleanup(player)
        player?.let {
            limpiarEntidades(it.uniqueId)
            detenerMusica(it.uniqueId)
        }
    }
}
