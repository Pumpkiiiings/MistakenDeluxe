package liric.mistaken.supervivientes.clases

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.Superviviente
import liric.mistaken.utils.CraftEngineUtils
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
 * Civil: La clase balanceada y versátil.
 */
class Civil : Superviviente("civil", "Civil") {

    private val pathBase = "supervivientes.civil.items"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val rocaKey = NamespacedKey("mistaken", "roca")

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientes()
        val skillKeys = listOf("habilidad1", "habilidad2", "habilidad3")

        skillKeys.forEach { key ->
            config.getString("$pathBase.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemCache[key] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, langConfig.getInt("$pathBase.habilidad1_cooldown", 30))) {
                usarAdrenalina(player)
                sendAbilityMessage(player, langConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, langConfig.getInt("$pathBase.habilidad2_cooldown", 45))) {
                usarInvisibilidad(player, langConfig.getString("$pathBase.habilidad2_mensaje_fin"))
                sendAbilityMessage(player, langConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, langConfig.getInt("$pathBase.habilidad3_cooldown", 20))) {
                lanzarRoca(player)
                sendAbilityMessage(player, langConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, key: String) {
        val msg = lang.getString("$pathBase.${key}_mensaje")
        if (!msg.isNullOrEmpty()) {
            player.sendMessage(mm.deserialize(msg.replace("{prefix}", plugin.gameManager.getPrefix())))
        }
        val soundName = lang.getString("$pathBase.${key}_sonido", "UI_BUTTON_CLICK")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemCache.isEmpty()) preLoadKit()

        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes")

        fun giveLocalizedSkill(slot: Int, key: String) {
            val item = itemCache[key]?.clone() ?: return
            langConfig.getString("$pathBase.${key}_nombre")?.let {
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
        val job = scope.launch {
            delay(5000)
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
                    player.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 0.8f, 1.2f)
                }
            }
        }
        trackJob(job)
    }

    private fun usarInvisibilidad(player: Player, mensajeFin: String?) {
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false, false))
        val job = scope.launch {
            delay(5000)
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline) {
                    mensajeFin?.let { player.sendMessage(mm.deserialize(it)) }
                    player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f)
                }
            }
        }
        trackJob(job)
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
        scope.coroutineContext.cancelChildren()
    }
}
