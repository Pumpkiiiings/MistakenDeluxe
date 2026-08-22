package liric.mistaken.utils.resourcepack

import io.th0rgal.oraxen.api.OraxenItems
import org.bukkit.inventory.ItemStack
import liric.mistaken.Mistaken

class OraxenHook : CustomItemProvider {
    override fun getItem(property: String): ItemStack? {
        try {
            val builder = OraxenItems.getItemById(property)
            if (builder != null) {
                return builder.build()
            }
        } catch (e: Exception) {
            Mistaken.instance.logger.warning("Critical failure when requesting item from Oraxen: $property")
            e.printStackTrace()
        }
        return null
    }
}
