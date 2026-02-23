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

class CharlieInferno : Asesino(
    "charlie",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.charlie.nombre", "<gradient:#ff4500:#ff8c00><b>CHARLIE INFERNO</b></gradient>", "asesinos_info")
) {

    private val path = "asesinos.charlie"
    private val sonidoId = "mistaken:charlieinferno"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
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

        armorKeys.forEach { key ->
            config.getString("$path.armadura.$key")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
                    itemKitCache[key] = item
                }
            }
        }

        itemKeys.forEach { key ->
            config.getString("$path.items.$key")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemKitCache[key] = item
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
        inv.armorContents = arrayOfNulls(4) // Limpieza total de armadura para Kotlin

        val configMecanica = plugin.configManager.getAsesinos() // El global (la raíz)
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info") // Los nombres traducidos

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            // 1. Buscamos el ID del ítem en el archivo de mecánicas (global)
            val id = if (isArmor) configMecanica.getString("asesinos.charlie.armadura.$key")
            else configMecanica.getString("asesinos.charlie.items.$key")

            if (id == null || id == "none") return

            // 2. Creamos el ítem (CraftEngine o Vanilla fallback)
            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            // 3. Le pegamos el nombre según el idioma del jugador (asesinos_info.yml)
            val namePath = if (key == "arma") "asesinos.charlie.habilidades_nombres.arma"
            else "asesinos.charlie.habilidades_nombres.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
            }

            // 4. Lo mandamos a su lugar
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

        // --- SOLTAR EL KIT COMPLETO ---
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

        // ¡Que empiece el corrido!
        iniciarMusicaCharlie(player)
    }

    private fun habilidadInfierno(player: Player) {
        player.getNearbyEntities(7.5, 7.5, 7.5).filterIsInstance<Player>().forEach { target ->
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
        val targets = player.getNearbyEntities(10.0, 10.0, 10.0).filterIsInstance<Player>().toMutableList()
        targets.add(player)
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
        scope.launch {
            var ticks = 0
            while (isActive && ticks < 40 && ice.isValid) {
                withContext(plugin.bukkitDispatcher) {
                    ice.teleport(ice.location.add(dir))
                    val hit = ice.getNearbyEntities(1.0, 1.0, 1.0).filterIsInstance<Player>().firstOrNull { !plugin.asesinoManager.esElAsesino(it) }
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
                    current.world.getNearbyEntities(current, 1.2, 1.2, 1.2).filterIsInstance<Player>().forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            victim.fireTicks = 100
                            plugin.gameManager.combatManager.takeDamage(victim)
                        }
                    }
                }
                delay(100)
            }
            withContext(plugin.bukkitDispatcher) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
                player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
            }
        }
        trackJob(job)
    }

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

        val anguloActual = angulos.getOrDefault(uuid, 0.0)
        val radio = 1.3
        for (i in entidades.indices) {
            val offset = if (i == 0) 0.0 else Math.PI
            val x = radio * cos(anguloActual + offset)
            val z = radio * sin(anguloActual + offset)
            entidades[i].teleport(player.location.clone().add(x, if (i == 0) 1.8 else 0.8, z))
        }
        angulos[uuid] = (anguloActual + 0.15) % (Math.PI * 2)
    }

    private fun iniciarMusicaCharlie(player: Player) {
        val uuid = player.uniqueId
        if (musicJobs.containsKey(uuid)) return

        val job = scope.launch {
            while (isActive && player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                withContext(plugin.bukkitDispatcher) {
                    player.world.players.forEach { p ->
                        if (p.location.distanceSquared(player.location) < 1600) {
                            p.stopSound(sonidoId, SoundCategory.RECORDS)
                            p.playSound(player.location, sonidoId, SoundCategory.RECORDS, 2.0f, 1.0f)
                        }
                    }
                }
                delay(74000) // 1480 ticks aprox
            }
            withContext(plugin.bukkitDispatcher) { detenerMusica(uuid) }
        }
        musicJobs[uuid] = job
        trackJob(job)
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

    private fun detenerMusica(uuid: UUID) {
        musicJobs.remove(uuid)?.cancel()
        Bukkit.getOnlinePlayers().forEach { it.stopSound(sonidoId, SoundCategory.RECORDS) }
    }

    private fun limpiarEntidades(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { limpiarEntidades(it.uniqueId); detenerMusica(it.uniqueId) }
        scope.coroutineContext.cancelChildren()
    }
}
