package liric.mistaken.roles.survivors.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.Survivor
import liric.mistaken.utils.hooks.CraftEngine
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Particle
import org.bukkit.configuration.file.FileConfiguration
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

/**
 * [LIRIC-MISTAKEN 2.0]
 * Raincoat Kid: El niño del impermeable.
 * FIX: Sistema de Equipamiento igual al del Killer Slasher (100% CraftEngine).
 */
class RaincoatKid : Survivor(
    "raincoatkid",
    PumpkingServiceManager.messages.getStrictString(null, "supervivientes.raincoatkid.nombre", "survivors_info")
) {

    private val pathBase = "supervivientes.raincoatkid"
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()
    private val STICK_KEY = NamespacedKey("mistaken", "kid_stick")

    override fun useSkill(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSurvivorConfig(this.id)
        val langConfig = PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("items.skill1_cooldown", 25))) {
                usarSprint(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("items.skill2_cooldown", 15))) {
                usarDash(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill2")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: FileConfiguration, mech: FileConfiguration, key: String) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) {
            player.sendMessage(ColorTranslator.translate(msg))
        }
        val soundName = mech.getString("$pathBase.items.${key}_sound", "ENTITY_BAT_TAKEOFF")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    // --- 🛠️ EQUIPAMIENTO (ESTILO SLASHER) ---
    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        player.getAttribute(Attribute.SCALE)?.baseValue = 0.8

        val langInfo = PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")
        val configMecanica = plugin.configManager.getSurvivorConfig(this.id) // El global supervivientes.yml

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("armor.$key")
            else configMecanica.getString("items.$key")

            if (id == null || id == "none") return

            // Intentamos sacar el ítem de CraftEngine
            val item = CraftEngine.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            // Si es el palo, le ponemos la marca
            if (key == "skill3") {
                val meta = item.itemMeta
                meta.persistentDataContainer.set(STICK_KEY, PersistentDataType.BYTE, 1.toByte())
                item.itemMeta = meta
            }

            // Le ponemos el nombre
            val namePath = "$pathBase.skill_names.$key"
            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(ColorTranslator.translate(it)) }
            }

            if (isArmor) {
                when(key) {
                    "helmet" -> inv.helmet = item
                    "chestplate" -> inv.chestplate = item
                    "leggings" -> inv.leggings = item
                    "boots" -> inv.boots = item
                }
            } else {
                inv.setItem(slot, item)
            }
        }

        deliver("helmet", 0, true)
        deliver("chestplate", 0, true)
        deliver("leggings", 0, true)
        deliver("boots", 0, true)

        deliver("skill1", 0)
        deliver("skill2", 1)
        deliver("skill3", 2)

        player.updateInventory()
    }

    // --- HABILIDADES ---
    private fun usarSprint(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 2))
        player.world.spawnParticle(Particle.CLOUD, player.location, 5, 0.2, 0.1, 0.2, 0.05)

        val task = player.scheduler.runDelayed(plugin, {
            if (player.isOnline) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
                player.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 1f, 0.8f)
                player.sendActionBar(ColorTranslator.translate("<red><i>*jadeo*</i>"))
            }
        }, null, 100L)

        task?.let { activeTasks.add(it) }
    }

    private fun usarDash(player: Player) {
        val dir = player.location.direction.normalize().multiply(1.8).setY(0.4)
        player.velocity = dir
        player.playSound(player.location, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.2f)
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
    }

    fun aplicarGolpePalo(victim: Player) {
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
        victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1f, 0.5f)
        victim.world.spawnParticle(Particle.CRIT, victim.eyeLocation, 10)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            it.getAttribute(Attribute.SCALE)?.baseValue = 1.0
            it.removePotionEffect(PotionEffectType.SPEED)
            it.removePotionEffect(PotionEffectType.SLOWNESS)
        }
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}





