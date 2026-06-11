package liric.mistaken.roles.supervivientes.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.supervivientes.Superviviente
import liric.mistaken.utils.hooks.CraftEngine
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
 * Repartidor: Soporte tÃ¡ctico y control de Ã¡rea.
 * OPTIMIZADO: SeparaciÃ³n MecÃ¡nica/Info + Schedulers.
 */
class Repartidor : Superviviente(
    "repartidor",
    Mistaken.instance.pumpking.lib.service.PumpkingServiceManager.messages.getRawString(null, "supervivientes.repartidor.nombre", "Repartidor", "supervivientes_info")
) {

    private val pathBase = "supervivientes.repartidor"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()
    private val pedidoKey = NamespacedKey("mistaken", "pedido")

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
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 30))) {
                usarBebidaEnergetica(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("$pathBase.items.habilidad2_cooldown", 25))) {
                lanzarPedido(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("$pathBase.items.habilidad3_cooldown", 15))) {
                usarDerrame(player)
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
        val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_GENERIC_EAT")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equipar(player: Player) {
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
    }

    private fun usarBebidaEnergetica(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 1))

        val task = player.scheduler.runDelayed(plugin, {
            if (player.isOnline) {
                player.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 80, 1))
                player.playSound(player.location, Sound.ENTITY_PLAYER_BURP, 0.8f, 0.8f)
            }
        }, null, 120L)

        task?.let { activeTasks.add(it) }
    }

    private fun lanzarPedido(player: Player) {
        player.launchProjectile(Snowball::class.java).apply {
            persistentDataContainer.set(pedidoKey, PersistentDataType.BYTE, 1.toByte())
        }
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f)
    }

    private fun usarDerrame(player: Player) {
        val blockLoc = player.location.block.location
        // SupervivienteHabilidadListener.marcarBloque(blockLoc)

        player.world.spawnParticle(Particle.ITEM_SLIME, player.location.add(0.0, 0.1, 0.0), 40, 0.5, 0.0, 0.5, 0.1)
        player.playSound(player.location, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.5f)

        val task = plugin.server.regionScheduler.runDelayed(plugin, blockLoc, {
            // SupervivienteHabilidadListener.desmarcarBloque(blockLoc)
            blockLoc.world.spawnParticle(Particle.DRIPPING_WATER, blockLoc.clone().add(0.5, 0.1, 0.5), 15, 0.2, 0.1, 0.2)
        }, 200L)

        task?.let { activeTasks.add(it) }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}

