package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.menu.MenuBase
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag

/**
 * [LIRIC-MISTAKEN 2.0]
 * ShopSelector: Menú principal de selección de tiendas.
 *
 * Optimización:
 * - Soporte total para multi-idioma (es, en, jp, etc.) por subcarpetas.
 * - Sin singletons compartidos (evita bugs visuales entre jugadores).
 * - Carga dinámica de ítems desde el YAML del idioma del jugador.
 */
class ShopSelector : MenuBase("tienda_principal") {

    /**
     * Esta función se ejecuta cada vez que un jugador abre el menú.
     * @param config Es el archivo YAML específico del idioma del jugador (ej: lang/jp/tienda_principal.yml)
     */
    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {

        // 1. Cargar ajustes de sonido del idioma actual
        val soundName = config.getString("ajustes.sonido-click", "UI_BUTTON_CLICK") ?: "UI_BUTTON_CLICK"
        val clickSound = try {
            Sound.valueOf(soundName.uppercase())
        } catch (e: Exception) {
            Sound.UI_BUTTON_CLICK
        }

        // 2. ITEM: ASESINOS
        val pathA = "items.asesinos"
        val matA = Material.matchMaterial(config.getString("$pathA.material", "NETHERITE_SWORD")!!) ?: Material.NETHERITE_SWORD

        val itemAsesinos = ItemBuilder.from(matA)
            .name(mm.deserialize(config.getString("$pathA.nombre", "")!!))
            .lore(config.getStringList("$pathA.lore").map { mm.deserialize(it) })
            .flags(*ItemFlag.entries.toTypedArray()) // Ocultamos atributos (limpieza visual)
            .asGuiItem {
                player.playSound(player.location, clickSound, 1f, 1f)
                // Abrimos la tienda de asesinos (que también detectará su idioma solo)
                plugin.asesinoTienda.abrir(player)
            }

        // 3. ITEM: SUPERVIVIENTES
        val pathS = "items.supervivientes"
        val matS = Material.matchMaterial(config.getString("$pathS.material", "IRON_CHESTPLATE")!!) ?: Material.IRON_CHESTPLATE

        val itemSurvivors = ItemBuilder.from(matS)
            .name(mm.deserialize(config.getString("$pathS.nombre", "")!!))
            .lore(config.getStringList("$pathS.lore").map { mm.deserialize(it) })
            .flags(*ItemFlag.entries.toTypedArray())
            .asGuiItem {
                player.playSound(player.location, clickSound, 1f, 1f)
                // Abrimos la tienda de supervivientes
                plugin.supervivienteTienda.abrir(player)
            }

        // 4. Colocar los ítems en sus slots (definidos en el YAML de ese idioma)
        gui.setItem(config.getInt("$pathA.slot", 11), itemAsesinos)
        gui.setItem(config.getInt("$pathS.slot", 15), itemSurvivors)
    }
}
