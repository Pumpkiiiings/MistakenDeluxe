package liric.mistaken.perks.types

import liric.mistaken.perks.Perk
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class ResiliencePerk : Perk(
    id = "resilience",
    icon = ItemStack(Material.IRON_CHESTPLATE)
) {
    // We will trigger this manually from DamageListener or Combat system when hit
    override fun onDamaged(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * 2, 1))
    }
}
