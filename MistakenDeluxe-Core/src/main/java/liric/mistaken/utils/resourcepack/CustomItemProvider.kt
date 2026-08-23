package liric.mistaken.utils.resourcepack

import org.bukkit.inventory.ItemStack

interface CustomItemProvider {
    fun getItem(property: String): ItemStack?
}
