package liric.mistaken.perks.types

import liric.mistaken.perks.Perk
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class AdrenalinePerk : Perk(
    id = "adrenaline",
    icon = ItemStack(Material.SUGAR)
) {
    override fun onGeneratorRepair(player: Player) {
        // Speed II for 3 seconds
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * 3, 1))
    }
}
