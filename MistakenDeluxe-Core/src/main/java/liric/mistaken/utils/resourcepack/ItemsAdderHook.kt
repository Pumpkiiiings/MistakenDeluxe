package liric.mistaken.utils.resourcepack

import dev.lone.itemsadder.api.CustomStack
import org.bukkit.inventory.ItemStack
import liric.mistaken.Mistaken

class ItemsAdderHook : CustomItemProvider {
    override fun getItem(property: String): ItemStack? {
        try {
            val stack = CustomStack.getInstance(property)
            if (stack != null) {
                return stack.itemStack
            }
        } catch (e: Exception) {
            Mistaken.instance.logger.warning("Critical failure when requesting item from ItemsAdder: $property")
            e.printStackTrace()
        }
        return null
    }
}
