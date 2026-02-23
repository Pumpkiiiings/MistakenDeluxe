package liric.mistaken.supervivientes.clases

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.listeners.supervivientes.SupervivienteHabilidadListener
import liric.mistaken.supervivientes.Superviviente
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
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
 * Repartidor: Soporte táctico y control de área.
 */
class Repartidor : Superviviente(
    "repartidor",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.repartidor.nombre", "Repartidor", "supervivientes")
) {

    private val pathBase = "supervivientes.repartidor.items"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pedidoKey = NamespacedKey("mistaken", "pedido")

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientes() // Global
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
                usarBebidaEnergetica(player)
                sendAbilityMessage(player, langConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, langConfig.getInt("$pathBase.habilidad2_cooldown", 25))) {
                lanzarPedido(player)
                sendAbilityMessage(player, langConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, langConfig.getInt("$pathBase.habilidad3_cooldown", 15))) {
                usarDerrame(player)
                sendAbilityMessage(player, langConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, key: String) {
        val msg = lang.getString("$pathBase.${key}_mensaje")
        if (!msg.isNullOrEmpty()) {
            player.sendMessage(mm.deserialize(msg.replace("{prefix}", plugin.gameManager.getPrefix())))
        }
        val soundName = lang.getString("$pathBase.${key}_sonido", "ENTITY_GENERIC_EAT")
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

    private fun usarBebidaEnergetica(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 1))
        val job = scope.launch {
            delay(6000)
            withContext(plugin.bukkitDispatcher) {
                if (player.isOnline) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 80, 1))
                    player.playSound(player.location, Sound.ENTITY_PLAYER_BURP, 0.8f, 0.8f)
                }
            }
        }
        trackJob(job)
    }

    private fun lanzarPedido(player: Player) {
        player.launchProjectile(Snowball::class.java).apply {
            persistentDataContainer.set(pedidoKey, PersistentDataType.BYTE, 1.toByte())
        }
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f)
    }

    private fun usarDerrame(player: Player) {
        val blockLoc = player.location.block.location
        SupervivienteHabilidadListener.marcarBloque(blockLoc)

        player.world.spawnParticle(Particle.ITEM_SLIME, player.location.add(0.0, 0.1, 0.0), 40, 0.5, 0.0, 0.5, 0.1)
        player.playSound(player.location, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.5f)

        val job = scope.launch {
            delay(10000)
            withContext(plugin.bukkitDispatcher) {
                SupervivienteHabilidadListener.desmarcarBloque(blockLoc)
                blockLoc.world.spawnParticle(Particle.DRIPPING_WATER, blockLoc.clone().add(0.5, 0.1, 0.5), 15, 0.2, 0.1, 0.2)
            }
        }
        trackJob(job)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        scope.coroutineContext.cancelChildren()
    }
}
