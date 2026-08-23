package liric.mistaken.utils.resourcepack

import com.nexomc.nexo.api.NexoItems
import org.bukkit.inventory.ItemStack
import liric.mistaken.Mistaken

class NexoHook : CustomItemProvider {
    override fun getItem(property: String): ItemStack? {
        try {
            val builder = NexoItems.itemFromId(property)
            if (builder != null) {
                return builder.build()
            }
        } catch (e: Exception) {
            Mistaken.instance.logger.warning("Critical failure when requesting item from Nexo: $property")
            e.printStackTrace()
        }
        return null
    }
}
