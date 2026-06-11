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
 * [LIRIC-MISTAKEN 2.1]
 * ShopSelector: Menú principal de selección de tiendas.
 *
 * Layout (slots, decoraciones) → menus/tienda_principal.yml  (GLOBAL)
 * Título y textos de items     → langs/<lang>/messages.yml   (POR IDIOMA)
 */
class ShopSelector : MenuBase("tienda_principal") {

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        val soundName = config.getString("ajustes.sonido-click", "BLOCK_NOTE_BLOCK_XYLOPHONE") ?: "BLOCK_NOTE_BLOCK_XYLOPHONE"
        val clickSound = runCatching { Sound.valueOf(soundName.uppercase()) }.getOrDefault(Sound.BLOCK_NOTE_BLOCK_XYLOPHONE)

        // --- ITEM: ASESINOS ---
        // Nombre y lore desde messages.yml del jugador (menus.tienda_principal.items.asesinos.*)
        val matA = Material.matchMaterial(
            config.getString("items.asesinos.material", "NETHERITE_SWORD")!!.uppercase()
        ) ?: Material.NETHERITE_SWORD

        val nombreA = getTranslatedString(player, "menus.tienda_principal.items.asesinos.nombre",
            "<gradient:red:dark_red><b>ASSASSIN SHOP</b></gradient>")

        val loreA = getTranslatedList(player, "menus.tienda_principal.items.asesinos.lore")
            .map { parseSafe(it) }

        val itemAsesinos = ItemBuilder.from(matA)
            .name(parseSafe(nombreA))
            .lore(loreA)
            .flags(*ItemFlag.entries.toTypedArray())
            .asGuiItem {
                player.playSound(player.location, clickSound, 1f, 1f)
                plugin.asesinoTienda.abrir(player)
            }

        // --- ITEM: SUPERVIVIENTES ---
        val matS = Material.matchMaterial(
            config.getString("items.supervivientes.material", "IRON_CHESTPLATE")!!.uppercase()
        ) ?: Material.IRON_CHESTPLATE

        val nombreS = getTranslatedString(player, "menus.tienda_principal.items.supervivientes.nombre",
            "<gradient:#00d4ff:#004d99><b>SURVIVOR SHOP</b></gradient>")

        val loreS = getTranslatedList(player, "menus.tienda_principal.items.supervivientes.lore")
            .map { parseSafe(it) }

        val itemSurvivors = ItemBuilder.from(matS)
            .name(parseSafe(nombreS))
            .lore(loreS)
            .flags(*ItemFlag.entries.toTypedArray())
            .asGuiItem {
                player.playSound(player.location, clickSound, 1f, 1f)
                plugin.supervivienteTienda.abrir(player)
            }

        gui.setItem(config.getInt("items.asesinos.slot", 11), itemAsesinos)
        gui.setItem(config.getInt("items.supervivientes.slot", 15), itemSurvivors)
    }
}
