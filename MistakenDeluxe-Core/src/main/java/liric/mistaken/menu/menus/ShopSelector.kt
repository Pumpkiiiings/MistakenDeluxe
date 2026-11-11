package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import liric.mistaken.api.util.Sounds
import liric.mistaken.menu.MenuBase
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag

/**
 * [LIRIC-MISTAKEN 2.1]
 * ShopSelector: Menu principal de seleccion de tiendas.
 */
class ShopSelector : MenuBase("main_shop") {

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        val soundName = config.getString("settings.click-sound", "BLOCK_NOTE_BLOCK_XYLOPHONE") ?: "BLOCK_NOTE_BLOCK_XYLOPHONE"
        val clickSound = Sounds.of(soundName, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE)

        val itemKillers = buildShopButton(
            player = player,
            config = config,
            sectionKey = "killers",
            defaultMaterial = Material.NETHERITE_SWORD,
            defaultName = "<gradient:red:dark_red><b>ASSASSIN SHOP</b></gradient>",
            clickSound = clickSound
        ) { plugin.killerTienda.abrir(player) }

        val itemSurvivors = buildShopButton(
            player = player,
            config = config,
            sectionKey = "survivors",
            defaultMaterial = Material.IRON_CHESTPLATE,
            defaultName = "<gradient:#00d4ff:#004d99><b>SURVIVOR SHOP</b></gradient>",
            clickSound = clickSound
        ) { plugin.survivorTienda.abrir(player) }

        gui.setItem(config.getInt("items.killers.slot", 11), itemKillers)
        gui.setItem(config.getInt("items.survivors.slot", 15), itemSurvivors)
    }

    private fun buildShopButton(
        player: Player,
        config: FileConfiguration,
        sectionKey: String,
        defaultMaterial: Material,
        defaultName: String,
        clickSound: Sound,
        onOpen: () -> Unit
    ): GuiItem {
        val mat = Material.matchMaterial(
            config.getString("items.$sectionKey.material", defaultMaterial.name)!!.uppercase()
        ) ?: defaultMaterial

        val name = getTranslatedString(player, "menus.main_shop.items.$sectionKey.name", defaultName)
        val lore = getTranslatedList(player, "menus.main_shop.items.$sectionKey.lore").map { parseSafe(it) }

        return ItemBuilder.from(mat)
            .name(parseSafe(name))
            .lore(lore)
            .asGuiItem {
                player.playSound(player.location, clickSound, 1f, 1f)
                onOpen()
            }.also { it.itemStack.editMeta { meta -> meta.addItemFlags(*ItemFlag.entries.toTypedArray()) } }
    }
}
