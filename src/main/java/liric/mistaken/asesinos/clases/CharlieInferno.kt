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
    Mistaken.Companion.instance.configManager.getAsesinos()
        .getString("asesinos.charlie.nombre", "<gradient:#ff4500:#ff8c00><b>CHARLIE INFERNO</b></gradient>")!!
) {

    private val path = "asesinos.charlie"
    private val sonidoId = "mistaken:charlieinferno"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val musicTasks = ConcurrentHashMap<UUID, BukkitRunnable>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k -> config.getString("$path.armadura.$k")?.let { id -> CraftEngineUtils.getCustomItem(id)?.let { itemKitCache[k] = it } } }
        items.forEach { k -> config.getString("$path.items.$k")?.let { id ->
            val name = config.getString("$path.items.${k}_nombre")
            CraftEngineUtils.getCustomItem(id)?.let { item ->
                name?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }
                itemKitCache[k] = item
            }
        } }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadInfierno(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadDemonRun(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadBloqueHielo(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadColmillosInfierno(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 🔥 H1: INFIERNO (15x15) ---
    private fun habilidadInfierno(player: Player) {
        player.getNearbyEntities(7.5, 7.5, 7.5).filterIsInstance<Player>().forEach { target ->
            if (!plugin.asesinoManager.esElAsesino(target)) {
                target.fireTicks = 100 // 5 segundos de fuego
                target.playSound(target.location, Sound.ITEM_FIRECHARGE_USE, 1f, 1f)
            }
        }
        player.world.spawnParticle(org.bukkit.Particle.FLAME, player.location, 50, 2.0, 0.5, 2.0, 0.1)
        // Debuff al asesino
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
    }

    // --- 🏃 H2: DEMON RUN (SPEED + LUEGO DEBUFF) ---
    private fun habilidadDemonRun(player: Player) {
        val targets = player.getNearbyEntities(10.0, 10.0, 10.0).filterIsInstance<Player>().toMutableList()
        targets.add(player) // Incluimos al asesino

        targets.forEach { it.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 0)) }

        scope.launch {
            delay(3000) // 3 segundos después
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

    // --- 🧊 H3: BLOQUE DE HIELO (PROYECTIL) ---
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
                        hit?.let {
                            it.freezeTicks = 140
                            it.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 2))
                        }
                        break
                    }
                    delay(50); ticks++
                }
                ice.remove()
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
            }
        }
    }

    // --- 🦷 H4: COLMILLOS DEL INFIERNO ---
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
                            plugin.combatManager.processTrueDamage(victim, player, 4.0)
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

    // --- 🚀 RESTO DE LÓGICA (Equipar, Orbitadores, etc) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (!itemKitCache.containsKey("casco")) preLoadKit()

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
        iniciarMusicaCharlie(player)
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarEntidades(uuid); return }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarEntidades(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            listOf(Material.MAGMA_BLOCK, Material.PACKED_ICE).map { mat ->
                player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                    bd.block = mat.createBlockData()
                    bd.transformation = Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf())
                    bd.teleportDuration = 2; bd.interpolationDuration = 2
                }
            }.toMutableList()
        }

        val angulo = angulos.getOrDefault(uuid, 0.0) + 0.15
        for (i in entidades.indices) {
            val offset = if (i == 0) 0.0 else Math.PI
            val x = 1.3 * cos(angulo + offset)
            val z = 1.3 * sin(angulo + offset)
            entidades[i].teleport(player.location.clone().add(x, if (i == 0) 1.8 else 0.8, z))
        }
        angulos[uuid] = angulo
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
