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
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.EvokerFangs
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
 *[LIRIC-MISTAKEN 2.0]
 * Charlie Inferno: El Heraldo de los Elementos.
 * FIX: Sistema de música 3D con atenuación natural y Coroutines.
 */
class CharlieInferno : Asesino(
    "charlie",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.charlie.nombre", "<gradient:#ff4500:#ff8c00><b>CHARLIE INFERNO</b></gradient>", "asesinos_info")
) {

    private val pathBase = "asesinos.charlie"
    // 🔥 EL ID DE TU MÚSICA (Debe durar unos 2-3 segundos por loop, o ser un sonido constante)
    private val sonidoId = "mistaken:charlieinferno"

    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()

    // Rastreador del loop de música
    private val musicJobs = ConcurrentHashMap<UUID, Job>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val orbitMaterials = listOf(Material.MAGMA_BLOCK, Material.PACKED_ICE)

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armorKeys.forEach { k ->
            config.getString("$pathBase.armadura.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                }
            }
        }

        itemKeys.forEach { k ->
            config.getString("$pathBase.items.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadInfierno(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadDemonRun(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadBloqueHielo(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadColmillosInfierno(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val configMecanica = plugin.configManager.getAsesinos()
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
            else configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "arma") "asesinos.charlie.habilidades_nombres.arma"
            else "asesinos.charlie.habilidades_nombres.$key"

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
            } else inv.setItem(slot, item)
        }

        deliver("casco", 0, true); deliver("pechera", 0, true)
        deliver("pantalones", 0, true); deliver("botas", 0, true)
        deliver("habilidad1", 1); deliver("habilidad2", 2)
        deliver("habilidad3", 3); deliver("habilidad4", 4)
        deliver("arma", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()

        // 🔥 ¡Que suene la rola del Diablo! 🔥
        iniciarMusicaCharlie(player)
    }

    // --- 🎵 MOTOR DE MÚSICA 3D ---

    /**
     * Inicia un bucle asíncrono que emite el sonido de Charlie constantemente.
     * La intensidad depende de la distancia gracias al motor nativo de Minecraft.
     */
    /**
     * Inicia un bucle asíncrono que emite el sonido de Charlie constantemente.
     * FIX: Evita que el sonido se empalme usando stopSound antes del playSound.
     */
    private fun iniciarMusicaCharlie(player: Player) {
        val uuid = player.uniqueId
        musicJobs.remove(uuid)?.cancel()

        val job = scope.launch {
            while (isActive && player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                withContext(plugin.bukkitDispatcher) {

                    // Detenemos el sonido anterior en todos lados para que no se empalme
                    Bukkit.getOnlinePlayers().forEach { p ->
                        p.stopSound(sonidoId, SoundCategory.RECORDS)
                    }

                    // 🔥 LA MAGIA: Le decimos al mundo que el sonido "sale" del Asesino.
                    // Al usar el 'player' como fuente en lugar de su location estática,
                    // Minecraft hace que la música lo siga a él y baje el volumen si los demás se alejan.
                    player.world.playSound(player, sonidoId, SoundCategory.RECORDS, 2.0f, 1.0f)
                }

                // Espera de la canción (Asegúrate de que este número sea la duración de tu OGG)
                delay(74000L)
            }
            withContext(plugin.bukkitDispatcher) { detenerMusica(uuid) }
        }
        musicJobs[uuid] = job
        trackJob(job)
    }

    private fun detenerMusica(uuid: UUID) {
        musicJobs.remove(uuid)?.cancel()
        Bukkit.getOnlinePlayers().forEach { it.stopSound(sonidoId, SoundCategory.RECORDS) }
    }

    // --- 🔥 HABILIDADES ---

    private fun habilidadInfierno(player: Player) {
        player.world.getNearbyPlayers(player.location, 7.5).forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.fireTicks = 100
                plugin.gameManager.combatManager.takeDamage(target)
                target.playSound(target.location, Sound.ITEM_FIRECHARGE_USE, 1f, 1f)
            }
        }
        player.world.spawnParticle(org.bukkit.Particle.FLAME, player.location, 50, 2.0, 0.5, 2.0, 0.1)
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
    }

    private fun habilidadDemonRun(player: Player) {
        val targets = player.world.getNearbyPlayers(player.location, 10.0).toMutableList()
        targets.forEach { it.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 0)) }

        scope.launch {
            delay(3000)
            withContext(plugin.bukkitDispatcher) {
                targets.forEach {
                    if (it.isOnline) {
                        it.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0))
                        it.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
                        it.playSound(it.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.8f)
                    }
                }
            }
        }
    }

    private fun habilidadBloqueHielo(player: Player) {
        val ice = player.world.spawn(player.eyeLocation, ItemDisplay::class.java) {
            it.setItemStack(ItemStack(Material.PACKED_ICE))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.6f, 0.6f, 0.6f), Quaternionf())
        }
        val dir = player.location.direction.multiply(1.2)

        val job = scope.launch {
            var ticks = 0
            while (isActive && ticks < 40 && ice.isValid) {
                withContext(plugin.bukkitDispatcher) {
                    ice.teleport(ice.location.add(dir))
                    val hit = player.world.getNearbyPlayers(ice.location, 1.0).firstOrNull { !plugin.asesinoManager.esElAsesino(it) }

                    if (hit != null || ice.location.block.type.isSolid) {
                        ice.world.spawnParticle(org.bukkit.Particle.SNOWFLAKE, ice.location, 30, 0.5, 0.5, 0.5, 0.1)
                        ice.world.playSound(ice.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
                        hit?.let {
                            it.freezeTicks = 140
                            it.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 2))
                        }
                        ice.remove()
                        cancel()
                    }
                }
                delay(50)
                ticks++
            }
            withContext(plugin.bukkitDispatcher) {
                if (ice.isValid) ice.remove()
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
            }
        }
        trackJob(job)
    }

    private fun habilidadColmillosInfierno(player: Player) {
        val direction = player.location.direction.setY(0.0).normalize()
        val startLoc = player.location.clone()
        val job = scope.launch {
            val current = startLoc.clone()
            repeat(12) {
                if (!isActive) return@launch
                withContext(plugin.bukkitDispatcher) {
                    current.add(direction)
                    current.world.spawn(current, EvokerFangs::class.java)
                    current.world.getNearbyPlayers(current, 1.5).forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            victim.fireTicks = 100
                            plugin.gameManager.combatManager.takeDamage(victim)
                        }
                    }
                }
                delay(100)
            }
        }
        trackJob(job)
    }

    // --- 🚀 VISUALES ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarEntidades(uuid); return }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarEntidades(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            orbitMaterials.map { mat ->
                player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                    bd.block = mat.createBlockData()
                    bd.transformation = Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf())
                    bd.teleportDuration = 2; bd.interpolationDuration = 2
                }
            }.toMutableList()
        }

        val anguloActual = (angulos.getOrDefault(uuid, 0.0) + 0.15) % (Math.PI * 2)
        val radio = 1.3
        for (i in entidades.indices) {
            val offset = if (i == 0) 0.0 else Math.PI
            val x = radio * cos(anguloActual + offset)
            val z = radio * sin(anguloActual + offset)
            entidades[i].teleport(player.location.clone().add(x, if (i == 0) 1.8 else 0.8, z))
        }
        angulos[uuid] = (anguloActual + 0.15) % (Math.PI * 2)
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.FLAME), false, Vector3d(loc.x, loc.y + 1.0, loc.z), Vector3f(0.15f, 0.15f, 0.15f), 0.02f, 2)
        loc.world.players.forEach {
            if (it != player && it.location.distanceSquared(loc) < 400.0) {
                PacketEvents.getAPI().playerManager.sendPacket(it, packet)
            }
        }
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
        scope.coroutineContext.cancelChildren()
    }
}
