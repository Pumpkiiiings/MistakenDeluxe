package liric.mistaken.roles.supervivientes.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.supervivientes.Superviviente
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

/**
 * [LIRIC-MISTAKEN 2.0]
 * Raincoat Kid: El niÃ±o del impermeable.
 * FIX: Sistema de Equipamiento igual al del Asesino Slasher (100% CraftEngine).
 */
class RaincoatKid : Superviviente(
    "raincoatkid",
    Mistaken.instance.pumpking.lib.service.PumpkingServiceManager.messages.getRawString(null, "supervivientes.raincoatkid.nombre", "Raincoat Kid", "supervivientes_info")
) {

    private val pathBase = "supervivientes.raincoatkid"
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()
    private val STICK_KEY = NamespacedKey("mistaken", "kid_stick")

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 25))) {
                usarSprint(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("$pathBase.items.habilidad2_cooldown", 15))) {
                usarDash(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad2")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, mech: org.bukkit.configuration.file.FileConfiguration, key: String) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) {
            player.sendMessage(mm.deserialize(msg))
        }
        val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_BAT_TAKEOFF")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    // --- ðŸ› ï¸ EQUIPAMIENTO (ESTILO SLASHER) ---
    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        player.getAttribute(Attribute.SCALE)?.baseValue = 0.8

        val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")
        val configMecanica = plugin.configManager.getSupervivientes() // El global supervivientes.yml

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
            else configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            // Intentamos sacar el Ã­tem de CraftEngine
            val item = CraftEngine.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            // Si es el palo, le ponemos la marca
            if (key == "habilidad3") {
                val meta = item.itemMeta
                meta.persistentDataContainer.set(STICK_KEY, PersistentDataType.BYTE, 1.toByte())
                item.itemMeta = meta
            }

            // Le ponemos el nombre
            val namePath = "$pathBase.habilidades_nombres.$key"
            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
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

        deliver("casco", 0, true)
        deliver("pechera", 0, true)
        deliver("pantalones", 0, true)
        deliver("botas", 0, true)

        deliver("habilidad1", 0)
        deliver("habilidad2", 1)
        deliver("habilidad3", 2)

        player.updateInventory()
    }

    // --- HABILIDADES ---
    private fun usarSprint(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 2))
        player.world.spawnParticle(org.bukkit.Particle.CLOUD, player.location, 5, 0.2, 0.1, 0.2, 0.05)

        val task = player.scheduler.runDelayed(plugin, {
            if (player.isOnline) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
                player.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 1f, 0.8f)
                player.sendActionBar(mm.deserialize("<red><i>*jadeo*</i>"))
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
        victim.world.spawnParticle(org.bukkit.Particle.CRIT, victim.eyeLocation, 10)
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

