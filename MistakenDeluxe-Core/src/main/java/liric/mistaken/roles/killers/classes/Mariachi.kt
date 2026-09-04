package liric.mistaken.roles.killers.classes

import liric.mistaken.utils.sessionViewers
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.roles.killers.Killer
import liric.mistaken.roles.killers.CoreKiller
import liric.mistaken.utils.resourcepack.CustomItemManager
import liric.mistaken.utils.hooks.ObserverHook
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
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualItemDisplay
import org.bukkit.plugin.java.JavaPlugin
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService

class Mariachi : CoreKiller(
    "mariachi",
    MessageService.getStrictString(null, "killers.mariachi.nombre", "killers_info")
) {

    private val pathBase = "killers.mariachi"
    override val defaultMusic = "mistaken:jarabetapatio"
    private val sonidoMúsicaId = defaultMusic

    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val skullsOrbit = ConcurrentHashMap<UUID, MutableList<VirtualItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getKillerConfig(this.id)
        val armor = listOf("helmet", "chestplate", "leggings", "boots")
        val items = listOf("weapon", "skill1", "skill2", "skill3", "skill4")

        armor.forEach { k ->
            config.getString("armor.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CustomItemManager.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
                }
            }
        }

        items.forEach { k ->
            config.getString("items.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CustomItemManager.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.GOLDEN_AXE)
                }
            }
        }
    }

    override fun useSkill(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { abilityGrito(player); playSkillEffects(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { abilityJarabe(player); playSkillEffects(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { abilityGuitarrazo(player); playSkillEffects(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { abilityTequila(player); playSkillEffects(player, 4) }
        }
    }

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        if (itemKitCache.isEmpty()) preLoadKit()
        val langInfo = MessageService.getSpecificFile(player, "killers_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val item = itemKitCache[key]?.clone() ?: return
            val namePath = if (key == "weapon") "killers.mariachi.skill_names.weapon" else "killers.mariachi.skill_names.$key"

            langInfo.getString(namePath)?.let { item.editMeta { m -> m.displayName(ColorTranslator.translate(it)) } }

            if (isArmor) {
                when(key) {
                    "helmet" -> inv.helmet = item
                    "chestplate" -> inv.chestplate = item
                    "leggings" -> inv.leggings = item
                    "boots" -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        listOf("helmet", "chestplate", "leggings", "boots").forEach { deliver(it, 0, true) }
        deliver("skill1", 1); deliver("skill2", 2); deliver("skill3", 3); deliver("skill4", 4); deliver("weapon", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
        iniciarMusica(player)
    }

    private fun abilityGrito(player: Player) {
        player.world.getNearbyPlayers(player.location, 8.0).forEach { victim ->
            
            if (isValidTarget(player, victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 1))
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 2))
                victim.sendMessage(ColorTranslator.translate(
                    liric.mistaken.config.engine.core.MessageService.getStrictString(victim, "killers.mariachi.habilidades.grito_corrompido", "killers_info")
                ))
                victim.playSound(victim.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.8f)
            }
        }
    }

    private fun abilityJarabe(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 3))
        player.sendMessage(ColorTranslator.translate(
            liric.mistaken.config.engine.core.MessageService.getStrictString(player, "killers.mariachi.habilidades.a_zapatear", "killers_info")
        ))
    }

    private fun abilityGuitarrazo(player: Player) {
        player.world.getNearbyPlayers(player.location, 6.0).forEach { victim ->
            
            if (isValidTarget(player, victim)) {
                plugin.combatManager.takeDamage(victim)
                victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.4)
                victim.playSound(victim.location, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f)
            }
        }
    }

    private fun abilityTequila(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 120, 4))
        player.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 160, 0))
        player.sendMessage(ColorTranslator.translate(
            liric.mistaken.config.engine.core.MessageService.getStrictString(player, "killers.mariachi.habilidades.salud_inmune", "killers_info")
        ))
    }

    override fun showPhysicalTrail(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.killerManager.isKiller(player)) { clearVisuales(uuid); return }
        if (skullsOrbit[uuid]?.firstOrNull()?.world != player.world) clearVisuales(uuid)

        val skulls = skullsOrbit.getOrPut(uuid) {
            mutableListOf<VirtualItemDisplay>().apply {
                repeat(3) {
                    add(PacketFactory.displays.buildItemDisplay(player.sessionViewers(), player.location) { id ->
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

    override fun showTrail(player: Player) {
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
            if (!player.isOnline || !plugin.killerManager.isKiller(player)) {
                detenerMusica(uuid)
                task.cancel()
                return@Consumer
            }
            player.world.players.forEach { p ->
                if (p.location.distanceSquared(player.location) < 1600) {
                    ObserverHook.stopSound(p, sonidoMúsicaId)
                    ObserverHook.playEntitySound(p, sonidoMúsicaId, player, 1.5f, 1.0f)
                }
            }
        }, null, 1L, 1480L)
    }

    private fun clearVisuales(uuid: UUID) {
        skullsOrbit.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    private fun detenerMusica(uuid: UUID) {
        Bukkit.getOnlinePlayers().forEach { ObserverHook.stopSound(it, sonidoMúsicaId) }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            clearVisuales(it.uniqueId)
            detenerMusica(it.uniqueId)
        }
    }
}
