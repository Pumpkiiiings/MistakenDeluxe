package liric.mistaken.roles.survivors.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.Survivor
import liric.mistaken.utils.hooks.CraftEngine
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.configuration.file.FileConfiguration
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

/**
 * [LIRIC-MISTAKEN 2.0]
 * Civilian: La clase balanceada y versátil.
 * OPTIMIZADO: Separación Mecánica/Info + Schedulers.
 */
class Civilian : Survivor(
    "civil",
    PumpkingServiceManager.messages.getStrictString(null, "supervivientes.civil.nombre", "survivors_info")
) {

    private val pathBase = "supervivientes.civil"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()
    private val rocaKey = NamespacedKey("mistaken", "roca")

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
        val langConfig = PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("items.skill1_cooldown", 30))) {
                usarAdrenalina(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("items.skill2_cooldown", 45))) {
                val mensajeFin = langConfig.getString("habilidades_mensajes.skill2_fin")
                usarInvisibilidad(player, mensajeFin)
                sendAbilityMessage(player, langConfig, mechConfig, "skill2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("items.skill3_cooldown", 20))) {
                lanzarRoca(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill3")
            }
        }
    }

    private fun sendAbilityMessage(
        player: Player,
        lang: FileConfiguration,
        mech: FileConfiguration,
        key: String
    ) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) {
            msg = msg.replace("<prefix>", "", true).replace("%prefix%", "", true).trim()
            player.sendMessage(ColorTranslator.translate(msg))
        }
        val soundName = mech.getString("$pathBase.items.${key}_sound", "UI_BUTTON_CLICK")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemCache.isEmpty()) preLoadKit()

        val langConfig = PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")

        fun giveLocalizedSkill(slot: Int, key: String) {
            val item = itemCache[key]?.clone() ?: return
            langConfig.getString("skill_names.$key")?.let {
                item.editMeta { m -> m.displayName(ColorTranslator.translate(it)) }
            }
            inv.setItem(slot, item)
        }

        giveLocalizedSkill(0, "skill1")
        giveLocalizedSkill(1, "skill2")
        giveLocalizedSkill(2, "skill3")

        player.updateInventory()
    }

    private fun usarAdrenalina(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))

        val task = player.scheduler.runDelayed(plugin, {
            if (player.isOnline) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
                player.playSound(player.location, Sound.ENTITY_HORSE_BREATHE, 0.8f, 0.6f)
            }
        }, null, 100L)

        task?.let { activeTasks.add(it) }
    }

    private fun usarInvisibilidad(player: Player, mensajeFin: String?) {
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false, false))

        val task = player.scheduler.runDelayed(plugin, {
            if (player.isOnline) {
                mensajeFin?.let { player.sendMessage(ColorTranslator.translate(it)) }
                player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f)
            }
        }, null, 100L)

        task?.let { activeTasks.add(it) }
    }

    private fun lanzarRoca(player: Player) {
        player.launchProjectile(Snowball::class.java).apply {
            persistentDataContainer.set(rocaKey, PersistentDataType.BYTE, 1.toByte())
        }
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.5f)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            it.removePotionEffect(PotionEffectType.DARKNESS)
            it.isSwimming = false
        }
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}






