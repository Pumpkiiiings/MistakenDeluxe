package liric.mistaken.perks.types

import liric.mistaken.perks.Perk
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class StealthPerk : Perk(
    id = "stealth",
    icon = ItemStack(Material.LEATHER_BOOTS)
) {
    // This perk is passive. The stealth logic will be checked by calling PerkManager#hasEquippedPerk("stealth")
}
