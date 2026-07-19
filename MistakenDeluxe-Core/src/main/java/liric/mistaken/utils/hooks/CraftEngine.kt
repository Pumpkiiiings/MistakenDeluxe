package liric.mistaken.utils.hooks

import liric.mistaken.Mistaken
import net.momirealms.craftengine.bukkit.item.BukkitItemManager
import net.momirealms.craftengine.core.util.Key
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * [LIRIC-MISTAKEN 2.0]
 * CraftEngineUtils: El puente definitivo para ítems custom y vanilla.
 * Optimizado para Paper 1.21.4 con debug inteligente.
 */
object CraftEngine {

    /**
     * Checa si el motor de CraftEngine está activo.
     */
    fun isAvailable(): Boolean = Bukkit.getPluginManager().isPluginEnabled("CraftEngine")

    /**
     * Resuelve el ítem buscando primero en CraftEngine y luego en Vanilla.
     */
    fun getCustomItem(property: String?): ItemStack? {
        // 1. Filtro rápido: si no hay nada o dice 'none', nos ahorramos el jale.
        if (property.isNullOrBlank() || property.equals("none", ignoreCase = true)) {
            return null
        }

        // 2. Intento por CraftEngine (usando la nueva API CraftEngineItems)
        if (property.contains(":") && isAvailable()) {
            try {
                val itemDef = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(property)
                if (itemDef != null) {
                    return itemDef.buildBukkitItem()
                }
            } catch (e: Exception) {
                Mistaken.Companion.instance.logger.warning("Fallo crítico al pedir ítem a CraftEngine: $property")
                e.printStackTrace()
            }
        }

        // 3. Fallback Vanilla: Si no tiene ':' o CraftEngine no lo halló.
        // Usamos matchMaterial porque es más aguantador que el valueOf
        val mat = Material.matchMaterial(property.uppercase())

        return if (mat != null && mat != Material.AIR) {
            ItemStack(mat)
        } else {
            // Si llegamos aquí, es que el admin escribió algo que no existe
            if (!property.contains(":")) {
                Mistaken.Companion.instance.logger.warning("¡Aviso! No se encontró el material vanilla: $property")
            }
            null
        }
    }

    /**
     * Versión para el equipo de los asesinos.
     * Si no encuentra el ítem, te da una barrera con el nombre del error.
     */
    fun getCustomItemSafe(property: String?): ItemStack {
        val item = getCustomItem(property)
        if (item != null) return item

        // Si falló, fabricamos un ítem de error para que el admin sepa qué onda
        return ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(pumpking.lib.color.ColorTranslator.translate("<red><bold>ERROR:</bold> <white>$property"))
                meta.lore(listOf(pumpking.lib.color.ColorTranslator.translate("<gray>Este ítem no se encontró en la config.")))
            }
        }
    }
}