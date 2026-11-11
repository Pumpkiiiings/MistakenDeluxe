package liric.mistaken.roles.killers.classes

import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualBlockDisplay
import liric.mistaken.roles.killers.CoreKiller
import liric.mistaken.utils.hooks.ObserverHook
import liric.mistaken.utils.misc.HitboxVisualizer
import liric.mistaken.utils.sessionViewers
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EvokerFangs
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

/** Visual and acoustic theme parameters differentiating Charlie killer variants. */
data class CharlieTheme(
    val orbitMaterials: List<Material>,
    val novaHitbox: Material,
    val novaTargetParticle: Particle,
    val novaTintR: Int,
    val novaTintG: Int,
    val novaTintB: Int,
    val novaSelfPrimaryParticle: Particle,
    val novaSelfSecondaryParticle: Particle,
    val demonHitbox: Material,
    val demonTargetParticle: Particle,
    val demonTintR: Int,
    val demonTintG: Int,
    val demonTintB: Int,
    val projectileItem: Material,
    val projectileHitbox: Material,
    val projectileTrailParticle: Particle,
    val projectileBreakBlock: Material,
    val projectileBreakSound: Sound,
    val fangsPrimaryParticle: Particle,
    val fangsSecondaryParticle: Particle,
    val fangsHitbox: Material,
    val trailParticle: Particle
)

/**
 * Shared parent class for Charlie killer variants ([CharlieInferno], [CharlieJazz]).
 * Parameterizes particles, hitboxes, sounds, and orbital visuals via [CharlieTheme].
 */
abstract class AbstractCharlieKiller(
    id: String,
    final override val defaultMusic: String,
    private val theme: CharlieTheme
) : CoreKiller(
    id,
    MessageService.getStrictString(null, "killers.$id.name", "killers_info")
) {
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<VirtualBlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()

    override fun onEquipped(player: Player) {
        startLoopingMusic(player, defaultMusic)
    }

    override fun useSkill(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { abilityInfierno(player); playSkillEffects(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { abilityDemonRun(player); playSkillEffects(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { abilityBloqueHielo(player); playSkillEffects(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { abilityColmillosInfierno(player); playSkillEffects(player, 4) }
        }
    }

    private fun abilityInfierno(player: Player) {
        HitboxVisualizer.drawInstantHitbox(plugin, player.location, 7.5, 7.5, 7.5, 10L, theme.novaHitbox)

        player.world.getNearbyPlayers(player.location, 7.5).forEach { target ->
            if (isValidTarget(player, target)) {
                target.fireTicks = 100
                plugin.combatManager.takeDamage(target)
                target.playSound(target.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)
                target.world.spawnParticle(theme.novaTargetParticle, target.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)
                ObserverHook.playScreenshake(target, 1.0f, 20)
                ObserverHook.playScreenTint(target, theme.novaTintR, theme.novaTintG, theme.novaTintB, 0.6f, 25)
            }
        }
        player.world.spawnParticle(theme.novaSelfPrimaryParticle, player.location, 80, 3.0, 0.5, 3.0, 0.1)
        player.world.spawnParticle(theme.novaSelfSecondaryParticle, player.location, 50, 3.0, 1.0, 3.0, 0.05)
        player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
    }

    private fun abilityDemonRun(player: Player) {
        HitboxVisualizer.drawInstantHitbox(plugin, player.location, 10.0, 10.0, 10.0, 20L, theme.demonHitbox)

        val targets = player.world.getNearbyPlayers(player.location, 10.0).toMutableList()
        targets.forEach { it.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 0)) }

        targets.forEach { target ->
            target.scheduler.runDelayed(plugin, Consumer { _ ->
                if (target.isOnline) {
                    target.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0))
                    target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
                    target.playSound(target.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.8f)
                    target.world.spawnParticle(theme.demonTargetParticle, target.location.add(0.0, 1.0, 0.0), 30, 0.5, 1.0, 0.5, 0.05)
                    ObserverHook.playScreenTint(target, theme.demonTintR, theme.demonTintG, theme.demonTintB, 0.5f, 30)
                }
            }, null, 60L)
        }
    }

    private fun abilityBloqueHielo(player: Player) {
        val ice = PacketFactory.displays.buildItemDisplay(player.sessionViewers(), player.eyeLocation) {
            it.setItemStack(ItemStack(theme.projectileItem))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.6f, 0.6f, 0.6f), Quaternionf())
        }
        val dir = player.location.direction.multiply(1.2)
        val hitbox = HitboxVisualizer.createHitbox(player.eyeLocation, 1.0, 1.0, 1.0, theme.projectileHitbox)

        var ticks = 0
        ice.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 40 || !ice.isValid) {
                if (ice.isValid) ice.remove()
                hitbox?.remove()
                player.scheduler.run(plugin, Consumer { _ ->
                    if (player.isOnline) player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
                }, null)
                task.cancel()
                return@Consumer
            }

            ice.teleport(ice.location.add(dir))
            hitbox?.teleport(ice.location)
            ice.world.spawnParticle(theme.projectileTrailParticle, ice.location, 5, 0.2, 0.2, 0.2, 0.01)

            val hit = player.world.getNearbyPlayers(ice.location, 1.0).firstOrNull { isValidTarget(player, it) }

            if (hit != null || ice.location.block.type.isSolid) {
                ice.world.spawnParticle(theme.projectileTrailParticle, ice.location, 50, 1.0, 1.0, 1.0, 0.1)
                ice.world.spawnParticle(Particle.BLOCK, ice.location, 40, 0.5, 0.5, 0.5, theme.projectileBreakBlock.createBlockData())
                ice.world.playSound(ice.location, theme.projectileBreakSound, 1f, 0.5f)
                hit?.let {
                    it.freezeTicks = 140
                    it.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 2))
                    hitbox?.block = Material.RED_STAINED_GLASS.createBlockData()
                    ObserverHook.playScreenTint(it, 100, 180, 255, 0.45f, 40)
                    ObserverHook.playScreenshake(it, 0.5f, 15)
                }
                ice.remove()

                player.scheduler.runDelayed(plugin, Consumer { _ -> hitbox?.remove() }, null, 2L)
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }

    private fun abilityColmillosInfierno(player: Player) {
        val direction = player.location.direction.setY(0.0).normalize()
        val current = player.location.clone()

        for (i in 0 until 12) {
            current.add(direction)
            val locToSpawn = current.clone()

            plugin.server.regionScheduler.runDelayed(plugin, locToSpawn, Consumer { _ ->
                locToSpawn.world.spawn(locToSpawn, EvokerFangs::class.java)
                locToSpawn.world.spawnParticle(theme.fangsPrimaryParticle, locToSpawn, 10, 0.2, 0.2, 0.2, 0.05)
                locToSpawn.world.spawnParticle(theme.fangsSecondaryParticle, locToSpawn, 5, 0.2, 0.5, 0.2, 0.05)

                HitboxVisualizer.drawInstantHitbox(plugin, locToSpawn, 1.5, 1.5, 1.5, 5L, theme.fangsHitbox)

                locToSpawn.world.getNearbyPlayers(locToSpawn, 1.5).forEach { victim ->
                    if (isValidTarget(player, victim)) {
                        victim.fireTicks = 100
                        plugin.combatManager.takeDamage(victim)
                        ObserverHook.playScreenTint(victim, 255, 0, 0, 0.4f, 10)
                    }
                }
            }, (i * 2 + 1).toLong())
        }
    }

    override fun showPhysicalTrail(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.killerManager.isKiller(player)) {
            clearEntidades(uuid)
            return
        }
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) clearEntidades(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            theme.orbitMaterials.map { mat ->
                PacketFactory.displays.buildBlockDisplay(player.sessionViewers(), player.location) { bd ->
                    bd.block = mat.createBlockData()
                    bd.transformation = Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf())
                    bd.teleportDuration = 2
                    bd.interpolationDuration = 2
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
        angulos[uuid] = anguloActual
    }

    override fun showTrail(player: Player) {
        val loc = player.location
        loc.world.players.forEach {
            if (it.location.distanceSquared(loc) < 2500) {
                it.spawnParticle(theme.trailParticle, loc.x, loc.y + 1.0, loc.z, 2, 0.15, 0.15, 0.15, 0.02)
            }
        }
    }

    private fun clearEntidades(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { clearEntidades(it.uniqueId) }
    }

    override fun onDispose() {
        orbitadores.values.forEach { list -> list.forEach { it.remove() } }
        orbitadores.clear()
        angulos.clear()
    }
}
