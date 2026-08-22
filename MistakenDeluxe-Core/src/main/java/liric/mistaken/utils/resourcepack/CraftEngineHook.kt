package liric.mistaken.utils.resourcepack

import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import net.momirealms.craftengine.core.util.Key
import org.bukkit.inventory.ItemStack
import liric.mistaken.Mistaken

class CraftEngineHook : CustomItemProvider {
    override fun getItem(property: String): ItemStack? {
        try {
            val itemDef = CraftEngineItems.byId(Key.from(property))
            if (itemDef != null) {
                return itemDef.buildBukkitItem()
            }
        } catch (e: Exception) {
            Mistaken.instance.logger.warning("Critical failure when requesting item from CraftEngine: $property")
            e.printStackTrace()
        }
        return null
    }
}
