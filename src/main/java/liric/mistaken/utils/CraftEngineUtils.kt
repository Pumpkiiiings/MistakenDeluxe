package liric.mistaken.utils

import liric.mistaken.Mistaken
import net.momirealms.craftengine.bukkit.item.BukkitItemManager
import net.momirealms.craftengine.core.util.Key
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * [LIRIC-MISTAKEN 2.0]
 * CraftEngineUtils: Bridge de compatibilidad para ítems custom y vanilla.
 * Optimizado para Paper 1.21.4+ con lógica de fallback ultra-rápida.
 */
object CraftEngineUtils {

    /**
     * Verifica si CraftEngine está instalado y activado.
     * En Paper 1.21.4+, acceder al PluginManager es una operación O(1) muy rápida.
     */
    fun isAvailable(): Boolean = Bukkit.getPluginManager().isPluginEnabled("CraftEngine")

    /**
     * Resuelve un String de configuración en un ItemStack real.
     * Soporta formatos: "namespace:id" (CraftEngine) o "MATERIAL_NAME" (Vanilla).
     */
    fun getCustomItem(property: String?): ItemStack? {
        // 1. Filtrado ultra-rápido de nulos o vacíos
        if (property.isNullOrBlank() || property.equals("none", ignoreCase = true)) {
            return null
        }

        // 2. Intento de carga por CraftEngine
        if (property.contains(":") && isAvailable()) {
            try {
                // Split optimizado con límite
                val split = property.split(":", limit = 2)
                if (split.size == 2) {
                    val key = Key.of(split[0], split[1])
                    val optionalItem = BukkitItemManager.instance().getCustomItem(key)

                    if (optionalItem.isPresent) {
                        return optionalItem.get().buildItemStack()
                    }
                }
            } catch (e: Exception) {
                Mistaken.instance.logger.warning("Fallo al resolver ítem en CraftEngine: $property")
            }
        }

        // 3. Fallback Vanilla (Match Material)
        // matchMaterial es más seguro que valueOf porque no tira excepciones
        return Material.matchMaterial(property.uppercase())?.let { material ->
            if (material != Material.AIR) ItemStack(material) else null
        }
    }

    /**
     * Versión a prueba de errores: Devuelve una BARRIER si el ítem no existe.
     * Evita que el inventario del asesino se rompa por un error de config.
     */
    fun getCustomItemSafe(property: String?): ItemStack {
        return getCustomItem(property) ?: ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Mistaken.mm.deserialize("<red><bold>ERROR:</bold> Ítem no encontrado"))
            }
        }
    }
}
