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
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

class Mariachi : Asesino(
    "mariachi",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.mariachi.nombre", "<gradient:#ff0000:#000000><b>MARIACHI MUERTE</b></gradient>", "asesinos_info")
) {

    private val path = "asesinos.mariachi"
    private val sonidoMúsicaId = "mistaken:jarabetapatio"

    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val musicTasks = ConcurrentHashMap<UUID, BukkitRunnable>()
    private val guitarras = ConcurrentHashMap<UUID, ItemDisplay>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$path.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("$path.items.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.GOLDEN_AXE)
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadGrito(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadJarabe(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadGuitarrazo(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadTequila(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        if (itemKitCache.isEmpty() || !itemKitCache.containsKey("casco")) preLoadKit()

        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun setLocalizedItem(slot: Int, key: String, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return

            val namePath = if (key == "arma") "asesinos.mariachi.habilidades_nombres.arma"
            else "asesinos.mariachi.habilidades_nombres.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { m -> m.displayName(mm.deserialize(it)) }
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

        listOf("casco", "pechera", "pantalones", "botas").forEach { setLocalizedItem(0, it, true) }
        setLocalizedItem(1, "habilidad1")
        setLocalizedItem(2, "habilidad2")
        setLocalizedItem(3, "habilidad3")
        setLocalizedItem(4, "habilidad4")
        setLocalizedItem(8, "arma")

        player.inventory.heldItemSlot = 8
        player.updateInventory()
        iniciarMusica(player)
    }

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
                val v = target.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.4)
                target.velocity = v

                plugin.gameManager.combatManager.takeDamage(target)
                target.playSound(target.location, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f)
            }
        }
    }

    private fun habilidadTequila(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 120, 4))
        player.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 160, 0))
        player.sendMessage(mm.deserialize("<green>¡Salud! Nada te hace daño ahora.</green>"))
    }

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val loc = player.location.add(0.0, 0.2, 0.0)
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val mgr = PacketEvents.getAPI().playerManager

        val flame = WrapperPlayServerParticle(Particle(ParticleTypes.SOUL_FIRE_FLAME), false, pos, Vector3f(0.15f, 0.1f, 0.15f), 0.02f, 1)

        Bukkit.getOnlinePlayers().forEach { p ->
            if (p != player && p.world == loc.world && p.location.distanceSquared(loc) < 625.0) {
                mgr.sendPacket(p, flame)
                if (ThreadLocalRandom.current().nextFloat() < 0.15f) {
                    mgr.sendPacket(p, WrapperPlayServerParticle(Particle(ParticleTypes.NOTE), false, pos.add(0.0, 1.8, 0.0), Vector3f(0.1f, 0.1f, 0.1f), 0.5f, 1))
                }
            }
        }
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }
        if (guitarras[uuid]?.world != player.world) limpiarVisuales(uuid)

        val display = guitarras.getOrPut(uuid) {
            player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                id.setItemStack(itemKitCache["arma"] ?: ItemStack(Material.GOLDEN_AXE))
                id.transformation = Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf().rotateY(Math.toRadians(180.0).toFloat()), JomlVector3f(0.8f, 0.8f, 0.8f), Quaternionf())
                id.teleportDuration = 2; id.interpolationDuration = 2
            }
        }

        val offset = player.location.direction.clone().multiply(-0.4)
        val loc = player.location.clone().add(offset).add(0.0, 1.1, 0.0)
        loc.yaw = player.location.yaw + 180f
        display.teleport(loc)
    }

    private fun iniciarMusica(player: Player) {
        val uuid = player.uniqueId
        detenerMusica(uuid)
        val runnable = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !plugin.asesinoManager.esElAsesino(player)) { detenerMusica(uuid); cancel(); return }
                player.location.world.players.forEach { p ->
                    if (p.location.distanceSquared(player.location) < 1600) {
                        p.stopSound(sonidoMúsicaId, SoundCategory.RECORDS)
                        p.playSound(player.location, sonidoMúsicaId, SoundCategory.RECORDS, 1.5f, 1.0f)
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
