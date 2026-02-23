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
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.mainThread
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

class CharlieInferno : Asesino(
    "charlie",
    // Nombre del asesino dinámico según el idioma por defecto del server
    Mistaken.instance.messageConfig.getSpecificFile(null, "asesinos").getString("asesinos.charlie.nombre", "Charlie Inferno")!!
) {

    private val path = "asesinos.charlie"
    private val sonidoId = "mistaken:charlieinferno"

    // Caché de ítems base (sin nombres) para optimizar RAM
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val musicTasks = ConcurrentHashMap<UUID, BukkitRunnable>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val orbitMaterials = listOf(Material.MAGMA_BLOCK, Material.PACKED_ICE)

    init {
        preLoadKit()
    }

    /**
     * 🔥 PRE-CARGA LÓGICA:
     * Carga los materiales y IDs de CraftEngine del archivo asesinos.yml de la RAIZ.
     */
    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinosConfig(null) // Archivo raíz
        val armorKeys = listOf("casco", "pechera", "pantalones", "botas")
        val itemKeys = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armorKeys.forEach { key ->
            config.getString("asesinos.charlie.armadura.$key")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
                    itemKitCache[key] = item
                }
            }
        }

        itemKeys.forEach { key ->
            config.getString("asesinos.charlie.items.$key")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemKitCache[key] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        // Mantuve tus slots tal cual (1, 2, 3, 4)
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadInfierno(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadDemonRun(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadBloqueHielo(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadColmillosInfierno(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🛠️ EQUIPAMIENTO (SISTEMA MULTI-IDIOMA) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Si el caché falló (CraftEngine no listo), reintentamos una vez
        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        // Obtenemos el archivo de lenguaje específico del jugador (lang/es/asesinos.yml)
        val langAsesinos = plugin.messageConfig.getSpecificFile(player, "asesinos")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            // 🔥 Buscamos el nombre traducido en la carpeta del idioma
            val namePath = if (key == "arma") "asesinos.charlie.items.arma_nombre"
            else "asesinos.charlie.items.${key}_nombre"

            val localizedName = langAsesinos.getString(namePath)
            if (localizedName != null) {
                item.editMeta { it.displayName(mm.deserialize(localizedName)) }
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

        // 1. Armadura
        setLocalizedItem(0, "casco", true)
        setLocalizedItem(0, "pechera", true)
        setLocalizedItem(0, "pantalones", true)
        setLocalizedItem(0, "botas", true)

        // 2. Hotbar (Mantuve tus posiciones: 1, 2, 3, 4 y Arma en 8)
        setLocalizedItem(1, "habilidad1")
        setLocalizedItem(2, "habilidad2")
        setLocalizedItem(3, "habilidad3")
        setLocalizedItem(4, "habilidad4")
        setLocalizedItem(8, "arma")

        player.inventory.heldItemSlot = 8
        player.updateInventory()
        iniciarMusicaCharlie(player)
    }

    // --- 🔥 HABILIDADES (Lógica Original) ---

    private fun habilidadInfierno(player: Player) {
        player.getNearbyEntities(7.5, 7.5, 7.5).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.fireTicks = 100
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
            withContext(plugin.mainThread) {
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
            withContext(plugin.mainThread) {
                while (ticks < 40 && ice.isValid) {
                    ice.teleport(ice.location.add(dir))
                    val hit = ice.getNearbyEntities(1.0, 1.0, 1.0).filterIsInstance<Player>().firstOrNull { !plugin.asesinoManager.esElAsesino(it) }
                    if (hit != null || ice.location.block.type.isSolid) {
                        ice.world.spawnParticle(org.bukkit.Particle.SNOWFLAKE, ice.location, 30, 0.5, 0.5, 0.5, 0.1)
                        ice.world.playSound(ice.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
                        hit?.let { it.freezeTicks = 140; it.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 2)) }
                        break
                    }
                    delay(50); ticks++
                }
                ice.remove()
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
            }
        }
    }

    private fun habilidadColmillosInfierno(player: Player) {
        val direction = player.location.direction.setY(0.0).normalize()
        val startLoc = player.location.clone()
        scope.launch {
            val current = startLoc.clone()
            repeat(12) {
                withContext(plugin.mainThread) {
                    current.add(direction)
                    current.world?.spawn(current, EvokerFangs::class.java)
                    current.world?.getNearbyEntities(current, 1.2, 1.2, 1.2)?.filterIsInstance<Player>()?.forEach { victim ->
                        if (!plugin.asesinoManager.esElAsesino(victim)) {
                            victim.fireTicks = 100
                            plugin.combatManager.processTrueDamage(victim, player, 3.0)
                        }
                    }
                }
                delay(100)
            }
            withContext(plugin.mainThread) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
                player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
            }
        }
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
        if (musicTasks.containsKey(uuid)) return
        val runnable = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !plugin.asesinoManager.esElAsesino(player)) { detenerMusica(uuid); cancel(); return }
                player.location.world.players.forEach { p ->
                    if (p.location.distanceSquared(player.location) < 1600) {
                        p.stopSound(sonidoId, SoundCategory.RECORDS)
                        p.playSound(player.location, sonidoId, SoundCategory.RECORDS, 2.0f, 1.0f)
                    }
                }
            }
        }
        trackTask(runnable.runTaskTimer(plugin, 0L, 1480L))
        musicTasks[uuid] = runnable
    }

    override fun mostrarTrail(player: Player) {
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.FLAME), false, Vector3d(player.location.x, player.location.y + 1.0, player.location.z), Vector3f(0.15f, 0.15f, 0.15f), 0.02f, 2)
        player.location.world.players.forEach { if (it != player && it.location.distanceSquared(player.location) < 400.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet) }
    }

    private fun detenerMusica(uuid: UUID) { musicTasks.remove(uuid)?.cancel(); Bukkit.getOnlinePlayers().forEach { it.stopSound(sonidoId, SoundCategory.RECORDS) } }
    private fun limpiarEntidades(uuid: UUID) { orbitadores.remove(uuid)?.forEach { it.remove() }; angulos.remove(uuid) }
    override fun cleanup(player: Player?) { super.cleanup(player); player?.let { limpiarEntidades(it.uniqueId); detenerMusica(it.uniqueId) } }
}
