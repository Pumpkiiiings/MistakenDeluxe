package liric.mistaken.utils.hooks

import liric.mistaken.Mistaken
import net.momirealms.craftengine.bukkit.item.BukkitItemManager
import net.momirealms.craftengine.core.util.Key
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import pumpking.lib.color.ColorTranslator

/**
 * [LIRIC-MISTAKEN 2.0]
 * CraftEngineUtils: El puente definitivo para �tems custom y vanilla.
 * Optimizado para Paper 1.21.4 con debug inteligente.
 */
object CraftEngine {

    /**
     * Checa si el motor de CraftEngine est� activo.
     */
    fun isAvailable(): Boolean = Bukkit.getPluginManager().isPluginEnabled("CraftEngine")

    /**
     * Resuelve el �tem buscando primero en CraftEngine y luego en Vanilla.
     */
    fun getCustomItem(property: String?): ItemStack? {
        // 1. Filtro r�pido: si no hay nada o dice 'none', nos ahorramos el jale.
        if (property.isNullOrBlank() || property.equals("none", ignoreCase = true)) {
            return null
        }

        // 2. Intento por CraftEngine (usando la nueva API CraftEngineItems)
        if (property.contains(":") && isAvailable()) {
            try {
                val itemDef = CraftEngineItems.byId(net.momirealms.craftengine.core.util.Key.from(property))
                if (itemDef != null) {
                    return itemDef.buildBukkitItem()
                }
            } catch (e: Exception) {
                Mistaken.Companion.instance.logger.warning("Fallo cr�tico al pedir �tem a CraftEngine: $property")
                e.printStackTrace()
            }
        }

        // 3. Fallback Vanilla: Si no tiene ':' o CraftEngine no lo hall�.
        // Usamos matchMaterial porque es m�s aguantador que el valueOf
        val mat = Material.matchMaterial(property.uppercase())

        return if (mat != null && mat != Material.AIR) {
            ItemStack(mat)
        } else {
            // Si llegamos aqu�, es que el admin escribi� algo que no existe
            if (!property.contains(":")) {
                Mistaken.Companion.instance.logger.warning("�Aviso! No se encontr� el material vanilla: $property")
            }
            null
        }
    }

    /**
     * Versi�n para el equipo de los killers.
     * Si no encuentra el �tem, te da una barrera con el nombre del error.
     */
    fun getCustomItemSafe(property: String?): ItemStack {
        val item = getCustomItem(property)
        if (item != null) return item

        // Si fall�, fabricamos un �tem de error para que el admin sepa qu� onda
        return ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(ColorTranslator.translate("<red><bold>ERROR:</bold> <white>$property"))
                meta.lore(listOf(ColorTranslator.translate("<gray>Este �tem no se encontr� en la config.")))
            }
        }
    }
}
