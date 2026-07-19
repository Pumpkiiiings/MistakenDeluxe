package liric.mistaken.roles.survivors.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.Survivor
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
 * DeliveryMan: Soporte tÃ¡ctico y control de Ã¡rea.
 * OPTIMIZADO: SeparaciÃ³n MecÃ¡nica/Info + Schedulers.
 */
class DeliveryMan : Survivor(
    "repartidor",
    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(null, "supervivientes.repartidor.nombre", "survivors_info")
) {

    private val pathBase = "supervivientes.repartidor"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()
    private val pedidoKey = NamespacedKey("mistaken", "pedido")

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
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("items.skill1_cooldown", 30))) {
                usarBebidaEnergetica(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("items.skill2_cooldown", 25))) {
                lanzarPedido(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("items.skill3_cooldown", 15))) {
                usarDerrame(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill3")
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
        val soundName = mech.getString("$pathBase.items.${key}_sound", "ENTITY_GENERIC_EAT")
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
        // SurvivorHabilidadListener.marcarBloque(blockLoc)

        player.world.spawnParticle(Particle.ITEM_SLIME, player.location.add(0.0, 0.1, 0.0), 40, 0.5, 0.0, 0.5, 0.1)
        player.playSound(player.location, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.5f)

        val task = plugin.server.regionScheduler.runDelayed(plugin, blockLoc, {
            // SurvivorHabilidadListener.desmarcarBloque(blockLoc)
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






