package liric.mistaken.roles.shared

import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.utils.resourcepack.CustomItemManager
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory

/**
 * Shared utility for resolving and equipping configurable role armor and skill items.
 */
object RoleItemUtils {

    private val NAMESPACE_PREFIX_REGEX = ".*:".toRegex()

    /** Resolves a custom or vanilla [ItemStack] from a config ID, returning null if missing or `"none"`. */
    fun resolveItem(itemId: String?, fallback: Material? = null): ItemStack? {
        if (itemId == null || itemId == "none" || itemId.isEmpty()) return null
        CustomItemManager.getCustomItem(itemId)?.let { return it }
        val matName = itemId.replace(NAMESPACE_PREFIX_REGEX, "").uppercase()
        val mat = Material.matchMaterial(matName) ?: fallback ?: return null
        return ItemStack(mat)
    }

    /** Equips a single armor piece or hotbar slot item resolved from [config] and localized via [langInfo]. */
    fun deliverConfigItem(
        inv: PlayerInventory,
        config: FileConfiguration,
        langInfo: FileConfiguration,
        langBasePath: String,
        key: String,
        slot: Int,
        isArmor: Boolean = false,
        fallback: Material? = null,
        customizer: ((ItemStack) -> Unit)? = null
    ) {
        val rawId = if (isArmor) config.getString("armor.$key") else config.getString("items.$key")
        val item = resolveItem(rawId, fallback) ?: return

        langInfo.getString("$langBasePath.skill_names.$key")?.let { name ->
            item.editMeta { meta -> meta.displayName(ColorTranslator.translate(name)) }
        }

        customizer?.invoke(item)

        if (isArmor) {
            when (key) {
                "helmet" -> inv.helmet = item
                "chestplate" -> inv.chestplate = item
                "leggings" -> inv.leggings = item
                "boots" -> inv.boots = item
            }
        } else {
            inv.setItem(slot, item)
        }
    }
}
