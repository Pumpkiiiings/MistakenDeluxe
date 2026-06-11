package liric.mistaken.roles.supervivientes.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.supervivientes.Superviviente
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

/**
 * [LIRIC-MISTAKEN 2.0]
 * Civil: La clase balanceada y versÃ¡til.
 * OPTIMIZADO: SeparaciÃ³n MecÃ¡nica/Info + Schedulers.
 */
class Civil : Superviviente(
    "civil",
    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(null, "supervivientes.civil.nombre", "supervivientes_info")
) {

    private val pathBase = "supervivientes.civil"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()
    private val rocaKey = NamespacedKey("mistaken", "roca")

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientes()
        listOf("habilidad1", "habilidad2", "habilidad3").forEach { key ->
            config.getString("$pathBase.items.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemCache[key] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 30))) {
                usarAdrenalina(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("$pathBase.items.habilidad2_cooldown", 45))) {
                val mensajeFin = langConfig.getString("$pathBase.habilidades_mensajes.habilidad2_fin")
                usarInvisibilidad(player, mensajeFin)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("$pathBase.items.habilidad3_cooldown", 20))) {
                lanzarRoca(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(
        player: Player,
        lang: org.bukkit.configuration.file.FileConfiguration,
        mech: org.bukkit.configuration.file.FileConfiguration,
        key: String
    ) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) {
            msg = msg.replace("<prefix>", "", true).replace("%prefix%", "", true).trim()
            player.sendMessage(mm.deserialize(msg))
        }
        val soundName = mech.getString("$pathBase.items.${key}_sonido", "UI_BUTTON_CLICK")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemCache.isEmpty()) preLoadKit()

        val langConfig = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "supervivientes_info")

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
                mensajeFin?.let { player.sendMessage(mm.deserialize(it)) }
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




