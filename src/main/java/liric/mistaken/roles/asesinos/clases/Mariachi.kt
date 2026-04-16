package liric.mistaken.roles.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.roles.asesinos.Asesino
import liric.mistaken.utils.hooks.CraftEngineHook
import org.bukkit.*
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
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class Mariachi : Asesino(
    "mariachi",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.mariachi.nombre", "<gradient:#ff0000:#000000><b>MARIACHI MUERTE</b></gradient>", "asesinos_info")
) {

    private val pathBase = "asesinos.mariachi"
    private val sonidoMúsicaId = "mistaken:jarabetapatio"

    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val skullsOrbit = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$pathBase.armadura.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineHook.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
                }
            }
        }

        items.forEach { k ->
            config.getString("$pathBase.items.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineHook.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.GOLDEN_AXE)
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
        inv.armorContents = arrayOfNulls(4)

        if (itemKitCache.isEmpty()) preLoadKit()
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return
            val namePath = if (key == "arma") "asesinos.mariachi.habilidades_nombres.arma" else "asesinos.mariachi.habilidades_nombres.$key"

            langInfo.getString(namePath)?.let { item.editMeta { m -> m.displayName(mm.deserialize(it)) } }

            if (isArmor) {
                when(key) {
                    "casco" -> inv.helmet = item
                    "pechera" -> inv.chestplate = item
                    "pantalones" -> inv.leggings = item
                    "botas" -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        listOf("casco", "pechera", "pantalones", "botas").forEach { deliver(it, 0, true) }
        deliver("habilidad1", 1); deliver("habilidad2", 2); deliver("habilidad3", 3); deliver("habilidad4", 4); deliver("arma", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
        iniciarMusica(player)
    }

    private fun habilidadGrito(player: Player) {
        player.world.getNearbyPlayers(player.location, 8.0).forEach { victim ->
            // 🔥 Uso de la función centralizada
            if (esObjetivoValido(player, victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 1))
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 2))
                victim.sendMessage(mm.deserialize("<red>¡El grito del Mariachi ha corrompido tus oídos!</red>"))
                victim.playSound(victim.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.8f)
            }
        }
    }

    private fun habilidadJarabe(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 3))
        player.sendMessage(mm.deserialize("<gold>¡A zapatear! Velocidad aumentada.</gold>"))
    }

    private fun habilidadGuitarrazo(player: Player) {
        player.world.getNearbyPlayers(player.location, 6.0).forEach { victim ->
            // 🔥 Uso de la función centralizada
            if (esObjetivoValido(player, victim)) {
                plugin.combatManager.takeDamage(victim)
                victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.4)
                victim.playSound(victim.location, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f)
            }
        }
    }

    private fun habilidadTequila(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 120, 4))
        player.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 160, 0))
        player.sendMessage(mm.deserialize("<green>¡Salud! Eres inmune al dolor por 6 segundos.</green>"))
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }
        if (skullsOrbit[uuid]?.firstOrNull()?.world != player.world) limpiarVisuales(uuid)

        val skulls = skullsOrbit.getOrPut(uuid) {
            mutableListOf<ItemDisplay>().apply {
                repeat(3) {
                    add(player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                        id.setItemStack(ItemStack(Material.PLAYER_HEAD))
                        id.transformation = Transformation(JomlVector3f(0f, 0f, 0f), Quaternionf(), JomlVector3f(0.6f, 0.6f, 0.6f), Quaternionf())
                        id.teleportDuration = 2; id.interpolationDuration = 2
                    })
                }
            }
        }

        val anguloActual = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        val radio = 1.3

        for (i in skulls.indices) {
            val offset = (2 * Math.PI / skulls.size) * i
            val x = radio * cos(anguloActual + offset)
            val z = radio * sin(anguloActual + offset)
            val y = 1.2 + (0.15 * sin((anguloActual + offset) * 2))

            val loc = player.location.clone().add(x, y, z)
            loc.yaw = ((anguloActual + offset) * 180 / Math.PI).toFloat()
            skulls[i].teleport(loc)
        }
        angulos[uuid] = anguloActual
    }

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val loc = player.location.add(0.0, 0.2, 0.0)
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val mgr = PacketEvents.getAPI().playerManager

        val flame = WrapperPlayServerParticle(Particle(ParticleTypes.SOUL_FIRE_FLAME), false, pos, Vector3f(0.15f, 0.1f, 0.15f), 0.02f, 1)

        player.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(loc) < 625.0) {
                mgr.sendPacket(p, flame)
                if (ThreadLocalRandom.current().nextFloat() < 0.15f) {
                    mgr.sendPacket(p, WrapperPlayServerParticle(Particle(ParticleTypes.NOTE), false, pos.add(0.0, 1.8, 0.0), Vector3f(0.1f, 0.1f, 0.1f), 0.5f, 1))
                }
            }
        }
    }

    private fun iniciarMusica(player: Player) {
        val uuid = player.uniqueId
        detenerMusica(uuid)

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!player.isOnline || !plugin.asesinoManager.esElAsesino(player)) {
                detenerMusica(uuid)
                task.cancel()
                return@Consumer
            }
            player.world.players.forEach { p ->
                if (p.location.distanceSquared(player.location) < 1600) {
                    p.stopSound(sonidoMúsicaId, SoundCategory.RECORDS)
                    p.playSound(player.location, sonidoMúsicaId, SoundCategory.RECORDS, 1.5f, 1.0f)
                }
            }
        }, null, 1L, 1480L)
    }

    private fun limpiarVisuales(uuid: UUID) {
        skullsOrbit.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    private fun detenerMusica(uuid: UUID) {
        Bukkit.getOnlinePlayers().forEach { it.stopSound(sonidoMúsicaId, SoundCategory.RECORDS) }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiarVisuales(it.uniqueId)
            detenerMusica(it.uniqueId)
        }
    }
}
