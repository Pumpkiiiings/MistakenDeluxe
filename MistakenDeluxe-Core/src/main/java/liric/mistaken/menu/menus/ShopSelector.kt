package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.api.util.Sounds
import liric.mistaken.menu.MenuBase
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag

/**
 * [LIRIC-MISTAKEN 2.1]
 * ShopSelector: Menú principal de selección de tiendas.
 *
 * Layout (slots, decoraciones) → menus/tienda_principal.yml  (GLOBAL)
 * Título y textos de items     → langs/<lang>/messages.yml   (POR IDIOMA)
 */
class ShopSelector : MenuBase("tienda_principal") {

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        val soundName = config.getString("ajustes.sonido-click", "BLOCK_NOTE_BLOCK_XYLOPHONE") ?: "BLOCK_NOTE_BLOCK_XYLOPHONE"
        val clickSound = Sounds.of(soundName, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE)

        
        
        val matA = Material.matchMaterial(
            config.getString("items.killers.material", "NETHERITE_SWORD")!!.uppercase()
        ) ?: Material.NETHERITE_SWORD

        val nombreA = getTranslatedString(player, "menus.tienda_principal.items.killers.nombre",
            "<gradient:red:dark_red><b>ASSASSIN SHOP</b></gradient>")

        val loreA = getTranslatedList(player, "menus.tienda_principal.items.killers.lore")
            .map { parseSafe(it) }

        val itemKillers = ItemBuilder.from(matA)
            .name(parseSafe(nombreA))
            .lore(loreA)
            .flags(*ItemFlag.entries.toTypedArray())
            .asGuiItem {
                player.playSound(player.location, clickSound, 1f, 1f)
                plugin.killerTienda.abrir(player)
            }

        
        val matS = Material.matchMaterial(
            config.getString("items.survivors.material", "IRON_CHESTPLATE")!!.uppercase()
        ) ?: Material.IRON_CHESTPLATE

        val nombreS = getTranslatedString(player, "menus.tienda_principal.items.survivors.nombre",
            "<gradient:#00d4ff:#004d99><b>SURVIVOR SHOP</b></gradient>")

        val loreS = getTranslatedList(player, "menus.tienda_principal.items.survivors.lore")
            .map { parseSafe(it) }

        val itemSurvivors = ItemBuilder.from(matS)
            .name(parseSafe(nombreS))
            .lore(loreS)
            .flags(*ItemFlag.entries.toTypedArray())
            .asGuiItem {
                player.playSound(player.location, clickSound, 1f, 1f)
                plugin.survivorTienda.abrir(player)
            }

        gui.setItem(config.getInt("items.killers.slot", 11), itemKillers)
        gui.setItem(config.getInt("items.survivors.slot", 15), itemSurvivors)
    }
}
