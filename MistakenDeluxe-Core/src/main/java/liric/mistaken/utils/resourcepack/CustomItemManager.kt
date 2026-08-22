package liric.mistaken.utils.resourcepack

import liric.mistaken.Mistaken
import liric.mistaken.utils.color.ColorTranslator
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object CustomItemManager {
    
    private var provider: CustomItemProvider? = null

    fun init() {
        val server = Mistaken.instance.server
        when {
            server.pluginManager.isPluginEnabled("Nexo") -> {
                provider = NexoHook()
                Mistaken.instance.componentLogger.info(ColorTranslator.translate("[INFO] Nexo found. This plugin will use it for custom items."))
            }
            server.pluginManager.isPluginEnabled("ItemsAdder") -> {
                provider = ItemsAdderHook()
                Mistaken.instance.componentLogger.info(ColorTranslator.translate("[INFO] ItemsAdder found. This plugin will use it for custom items."))
            }
            server.pluginManager.isPluginEnabled("Oraxen") -> {
                provider = OraxenHook()
                Mistaken.instance.componentLogger.info(ColorTranslator.translate("[INFO] Oraxen found. This plugin will use it for custom items."))
            }
            server.pluginManager.isPluginEnabled("CraftEngine") -> {
                provider = CraftEngineHook()
                Mistaken.instance.componentLogger.info(ColorTranslator.translate("[INFO] CraftEngine found. This plugin will use it for custom items."))
            }
            else -> {
                Mistaken.instance.componentLogger.warn(ColorTranslator.translate("[WARN] Nothing plugin found for textures. Using items vanilla..."))
            }
        }
    }

    fun getCustomItem(property: String?): ItemStack? {
        if (property.isNullOrBlank() || property.equals("none", ignoreCase = true)) {
            return null
        }

        if (property.contains(":")) {
            val custom = provider?.getItem(property)
            if (custom != null) {
                return custom
            }
        }

        val mat = Material.matchMaterial(property.uppercase())
        return if (mat != null && mat != Material.AIR) {
            ItemStack(mat)
        } else {
            if (!property.contains(":")) {
                Mistaken.instance.logger.warning("¡Aviso! No se encontró el material vanilla: $property")
            }
            null
        }
    }

    fun getCustomItemSafe(property: String?): ItemStack {
        val item = getCustomItem(property)
        if (item != null) return item
        
        return ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(ColorTranslator.translate("<red><bold>ERROR:</bold> <white>$property"))
                meta.lore(listOf(ColorTranslator.translate("<gray>Este ítem no se encontró en la config.")))
            }
        }
    }
}
