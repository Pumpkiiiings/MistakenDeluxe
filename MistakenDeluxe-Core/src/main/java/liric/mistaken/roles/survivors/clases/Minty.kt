package liric.mistaken.roles.survivors.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.Survivor
import liric.mistaken.utils.hooks.CraftEngine
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Minty: El Survivor LicÃ¡ntropo.
 * Rol: Control de Masas (CC) y Huida.
 * FIX: Null-Safety en activeTasks.add()
 */
class Minty : Survivor(
    "minty",
    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(null, "supervivientes.minty.nombre", "survivors_info")
) {

    private val pathBase = "supervivientes.minty"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getSurvivorConfig(this.id)
        listOf("skill1", "skill2", "skill3").forEach { key ->
            config.getString("items.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemCache[key] = item
                }
            }
        }
    }

    override fun useSkill(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSurvivorConfig(this.id)
        val langConfig = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("items.skill1_cooldown", 15))) {
                usarSarpazo(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("items.skill2_cooldown", 20))) {
                usarEmbestida(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("items.skill3_cooldown", 30))) {
                usarAullidoFeroz(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill3")
            }
        }
    }

    private fun usarSarpazo(player: Player) {
        val range = 4.0
        val ray = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, range) {
            it is Player && plugin.asesinoManager.isKiller(it)
        }

        player.world.spawnParticle(Particle.SWEEP_ATTACK, player.location.add(player.location.direction.multiply(1.5)).add(0.0, 1.2, 0.0), 1)

        if (ray != null && ray.hitEntity is Player) {
            val killer = ray.hitEntity as Player
            killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
            killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
            player.playSound(player.location, Sound.ENTITY_WOLF_GROWL, 1f, 0.8f)
            killer.playSound(killer.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f)
        } else {
            player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f)
        }
    }

    private fun usarEmbestida(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))
        player.velocity = player.location.direction.multiply(1.3).setY(0.3)

        val task = player.scheduler.runAtFixedRate(plugin, { t ->
            if (!player.isOnline) {
                t.cancel()
                return@runAtFixedRate
            }
            player.world.spawnParticle(Particle.CLOUD, player.location, 2, 0.2, 0.1, 0.2, 0.05)
        }, null, 1L, 5L)

        // SOLUCIÃ“N AL ERROR: Guardar solo si la tarea no es nula
        task?.let { activeTasks.add(it) }

        // Cancelar a los 5 segundos
        player.scheduler.runDelayed(plugin, { task?.cancel() }, null, 100L)
    }

    private fun usarAullidoFeroz(player: Player) {
        player.world.spawnParticle(Particle.SONIC_BOOM, player.location.add(0.0, 1.5, 0.0), 1)

        val targets = player.getNearbyEntities(8.0, 8.0, 8.0)
            .filterIsInstance<Player>()
            .filter { plugin.asesinoManager.isKiller(it) }

        targets.forEach { killer ->
            killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
            killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
            killer.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
            killer.velocity = killer.velocity.add(org.bukkit.util.Vector(0.0, 0.4, 0.0))
            killer.damage(0.0)
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, mech: org.bukkit.configuration.file.FileConfiguration, key: String) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) {
            msg = msg.replace("<prefix>", "", true).replace("%prefix%", "", true).trim()
            player.sendMessage(mm.deserialize(msg))
        }
        val soundName = mech.getString("$pathBase.items.${key}_sound", "UI_BUTTON_CLICK")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemCache.isEmpty()) preLoadKit()

        val langConfig = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")

        fun giveLocalizedSkill(slot: Int, key: String) {
            val item = itemCache[key]?.clone() ?: return
            langConfig.getString("skill_names.$key")?.let {
                item.editMeta { m -> m.displayName(mm.deserialize(it)) }
            }
            inv.setItem(slot, item)
        }

        giveLocalizedSkill(0, "skill1")
        giveLocalizedSkill(1, "skill2")
        giveLocalizedSkill(2, "skill3")

        player.updateInventory()
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.removePotionEffect(PotionEffectType.SPEED)
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}






