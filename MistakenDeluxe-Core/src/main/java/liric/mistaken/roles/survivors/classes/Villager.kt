package liric.mistaken.roles.survivors.classes

import liric.mistaken.roles.shared.RoleItemUtils
import liric.mistaken.roles.survivors.Survivor
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
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService


class Villager : Survivor(
    "aldeano",
    MessageService.getStrictString(null, "survivors.aldeano.name", "survivors_info")
) {

    val EMERALD_KEY = NamespacedKey("mistaken", "villager_emerald")

    override fun useSkill(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSurvivorConfig(this.id)
        val langConfig = MessageService.getSpecificFile(player, "survivors_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("items.skill1_cooldown", 20))) {
                usarPanico(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill1", "ENTITY_VILLAGER_YES")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("items.skill2_cooldown", 15))) {
                lanzarSoborno(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill2", "ENTITY_VILLAGER_YES")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("items.skill3_cooldown", 40))) {
                invocarGolem(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill3", "ENTITY_VILLAGER_YES")
            }
        }
    }

    override fun equip(player: Player) {
        equipFromConfig(
            player,
            armorFallbacks = mapOf(
                "helmet" to Material.LEATHER_HELMET,
                "chestplate" to Material.LEATHER_CHESTPLATE,
                "leggings" to Material.LEATHER_LEGGINGS,
                "boots" to Material.LEATHER_BOOTS
            ),
            itemFallbacks = mapOf(
                "skill1" to Material.EMERALD,
                "skill2" to Material.EMERALD,
                "skill3" to Material.EMERALD
            )
        )
    }

    
    private fun usarPanico(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 2)) 
        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
        player.world.spawnParticle(Particle.ANGRY_VILLAGER, player.location.add(0.0, 2.0, 0.0), 5)
    }

    
    private fun lanzarSoborno(player: Player) {
        val itemId = plugin.configManager.getSurvivorConfig(this.id).getString("items.skill2")
        val item = RoleItemUtils.resolveItem(itemId, Material.EMERALD) ?: ItemStack(Material.EMERALD)

        val proj = player.launchProjectile(Snowball::class.java)
        proj.item = item
        proj.persistentDataContainer.set(EMERALD_KEY, PersistentDataType.BYTE, 1.toByte())

        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 1f)
    }

    
    private fun invocarGolem(player: Player) {
        player.world.playSound(player.location, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f)
        player.world.spawnParticle(Particle.BLOCK_CRUMBLE, player.location, 30, 2.0, 0.5, 2.0, Material.IRON_BLOCK.createBlockData())

        
        player.world.getNearbyPlayers(player.location, 5.0).forEach { victim ->
            val session = plugin.sessionManager.getSession(victim)
            if (session?.isKiller(victim.uniqueId) == true) {
                
                val knockback = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(2.5).setY(0.5)
                victim.velocity = knockback

                victim.playSound(victim.location, Sound.ENTITY_IRON_GOLEM_HURT, 1f, 1f)
                victim.sendMessage(ColorTranslator.translate(
                    liric.mistaken.config.engine.core.MessageService.getStrictString(victim, "survivors.aldeano.habilidades.golem_rechazado", "survivors_info")
                ))
            }
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.removePotionEffect(PotionEffectType.SPEED)
    }
}
