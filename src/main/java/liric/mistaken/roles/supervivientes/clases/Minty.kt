package liric.mistaken.roles.supervivientes.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.supervivientes.Superviviente
import liric.mistaken.utils.hooks.CraftEngineHook
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 *[LIRIC-MISTAKEN 2.0]
 * Minty: El Superviviente Licántropo.
 * Rol: Control de Masas (CC) y Huida.
 * FIX: Adaptado a Folia y Null-Safety.
 */
class Minty : Superviviente(
    "minty",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.minty.nombre", "<gradient:#55ffaa:#00aa55><b>MINTY</b></gradient>", "supervivientes_info")
) {

    private val pathBase = "supervivientes.minty"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientes()
        listOf("habilidad1", "habilidad2", "habilidad3").forEach { key ->
            config.getString("$pathBase.items.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngineHook.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemCache[key] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 15))) {
                usarSarpazo(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("$pathBase.items.habilidad2_cooldown", 20))) {
                usarEmbestida(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("$pathBase.items.habilidad3_cooldown", 30))) {
                usarAullidoFeroz(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad3")
            }
        }
    }

    private fun usarSarpazo(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val range = 4.0
            val ray = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, range) {
                it is Player && plugin.asesinoManager?.esElAsesino(it) == true
            }

            player.world.spawnParticle(Particle.SWEEP_ATTACK, player.location.add(player.location.direction.multiply(1.5)).add(0.0, 1.2, 0.0), 1)

            if (ray != null && ray.hitEntity is Player) {
                val killer = ray.hitEntity as Player
                killer.scheduler.run(plugin, { _ ->
                    killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                    killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                    killer.playSound(killer.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f)
                }, null)
                player.playSound(player.location, Sound.ENTITY_WOLF_GROWL, 1f, 0.8f)
            } else {
                player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f)
            }
        }, null)
    }

    private fun usarEmbestida(player: Player) {
        player.scheduler.run(plugin, { _ ->
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))
            player.velocity = player.location.direction.multiply(1.3).setY(0.3)

            val task = player.scheduler.runAtFixedRate(plugin, Consumer { t ->
                if (!player.isOnline) {
                    t.cancel()
                    return@Consumer
                }
                player.world.spawnParticle(Particle.CLOUD, player.location, 2, 0.2, 0.1, 0.2, 0.05)
            }, null, 1L, 5L)

            task?.let { activeTasks.add(it) }

            player.scheduler.runDelayed(plugin, { _ -> task?.cancel() }, null, 100L)
        }, null)
    }

    private fun usarAullidoFeroz(player: Player) {
        player.scheduler.run(plugin, { _ ->
            player.world.spawnParticle(Particle.SONIC_BOOM, player.location.add(0.0, 1.5, 0.0), 1)

            val session = plugin.sessionManager?.getSession(player)

            player.world.getNearbyPlayers(player.location, 8.0).forEach { killer ->
                if (session != null && session.esAsesino(killer.uniqueId)) {
                    killer.scheduler.run(plugin, { _ ->
                        killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                        killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
                        killer.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                        killer.velocity = killer.velocity.add(Vector(0.0, 0.4, 0.0))
                    }, null)
                }
            }
        }, null)
    }

    private fun sendAbilityMessage(player: Player, lang: FileConfiguration, mech: FileConfiguration, key: String) {
        player.scheduler.run(plugin, { _ ->
            var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
            if (!msg.isNullOrEmpty()) {
                msg = msg.replace("<prefix>", "", true).replace("%prefix%", "", true).trim()
                player.sendMessage(mm.deserialize(msg))
            }
            val soundName = mech.getString("$pathBase.items.${key}_sonido", "UI_BUTTON_CLICK")
            runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
        }, null)
    }

    override fun equipar(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val inv = player.inventory
            inv.clear()
            if (itemCache.isEmpty()) preLoadKit()

            val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

            fun giveLocalizedSkill(slot: Int, key: String) {
                val item = itemCache[key]?.clone() ?: return
                langConfig.getString("$pathBase.habilidades_nombres.$key")?.let {
                    item.editMeta { m -> m.displayName(mm.deserialize(it)) }
                }
                inv.setItem(slot, item)
            }

            giveLocalizedSkill(0, "habilidad1")
            giveLocalizedSkill(1, "habilidad2")
            giveLocalizedSkill(2, "habilidad3")

            player.updateInventory()
        }, null)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.scheduler?.run(plugin, { _ ->
            player.removePotionEffect(PotionEffectType.SPEED)
        }, null)
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}
