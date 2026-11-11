package liric.mistaken.roles.survivors.classes

import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.roles.survivors.Survivor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * [LIRIC-MISTAKEN 2.0]
 * DeliveryMan: Soporte tactico y control de area.
 * OPTIMIZADO: Separacion Mecanica/Info + Schedulers.
 */
class DeliveryMan : Survivor(
    "repartidor",
    MessageService.getStrictString(null, "survivors.repartidor.name", "survivors_info")
) {

    private val pedidoKey = NamespacedKey("mistaken", "pedido")

    override fun useSkill(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSurvivorConfig(this.id)
        val langConfig = MessageService.getSpecificFile(player, "survivors_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("items.skill1_cooldown", 30))) {
                usarBebidaEnergetica(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill1", "ENTITY_GENERIC_EAT")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("items.skill2_cooldown", 25))) {
                lanzarPedido(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill2", "ENTITY_GENERIC_EAT")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("items.skill3_cooldown", 15))) {
                usarDerrame(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill3", "ENTITY_GENERIC_EAT")
            }
        }
    }

    override fun equip(player: Player) {
        equipFromConfig(
            player,
            includeArmor = false,
            itemFallbacks = mapOf(
                "skill1" to Material.PAPER,
                "skill2" to Material.PAPER,
                "skill3" to Material.PAPER
            )
        )
    }

    private fun usarBebidaEnergetica(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 1))

        val task = player.scheduler.runDelayed(plugin, {
            if (player.isOnline) {
                player.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 80, 1))
                player.playSound(player.location, Sound.ENTITY_PLAYER_BURP, 0.8f, 0.8f)
            }
        }, null, 120L)

        trackTask(player.uniqueId, task)
    }

    private fun lanzarPedido(player: Player) {
        player.launchProjectile(Snowball::class.java).apply {
            persistentDataContainer.set(pedidoKey, PersistentDataType.BYTE, 1.toByte())
        }
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f)
    }

    private fun usarDerrame(player: Player) {
        val blockLoc = player.location.block.location

        player.world.spawnParticle(Particle.ITEM_SLIME, player.location.add(0.0, 0.1, 0.0), 40, 0.5, 0.0, 0.5, 0.1)
        player.playSound(player.location, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.5f)

        val task = plugin.server.regionScheduler.runDelayed(plugin, blockLoc, {
            blockLoc.world.spawnParticle(Particle.DRIPPING_WATER, blockLoc.clone().add(0.5, 0.1, 0.5), 15, 0.2, 0.1, 0.2)
        }, 200L)

        trackTask(player.uniqueId, task)
    }
}
