package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.PrivateGameSettings
import liric.mistaken.game.enums.MistakenMode
import org.bukkit.Material
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator

class ModeSelectorMenu(private val plugin: Mistaken, private val session: GameSession) {

    fun abrir(player: Player) {
        val config = pumpking.lib.config.ConfigManager.getMenuConfig("private_lobby")
        val title = config.getString("menus.mode_selector.title", "<dark_gray>Seleccionar Modo") ?: "<dark_gray>Seleccionar Modo"
        val loreClick = config.getString("menus.mode_selector.items.mode.lore_click", "<e>Click para seleccionar") ?: "<e>Click para seleccionar"
        val backName = config.getString("items.back.name", "<red>Volver") ?: "<red>Volver"

        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic>$title"))
            .rows(3)
            .disableAllInteractions()
            .create()

        val settings = session.settings ?: PrivateGameSettings().also { session.settings = it }

        var slot = 11
        for (mode in MistakenMode.values()) {
            val isSelected = settings.forcedMode == mode
            val mat = if (isSelected) Material.DIAMOND_SWORD else Material.IRON_SWORD
            val color = if (isSelected) "<green><bold>" else "<yellow>"
            
            val item = ItemBuilder.from(mat)
                .name(ColorTranslator.translate("<!italic>$color${mode.name}"))
                .lore(
                    net.kyori.adventure.text.Component.empty(),
                    ColorTranslator.translate("<!italic>$loreClick")
                )
                .asGuiItem {
                    settings.forcedMode = if (isSelected) null else mode
                    abrir(player)
                }

            gui.setItem(slot++, item)
            if (slot == 16) break
        }

        gui.setItem(22, ItemBuilder.from(Material.ARROW)
            .name(ColorTranslator.translate("<!italic>$backName"))
            .asGuiItem {
                PrivateLobbyMenu(plugin, session).abrir(player)
            })

        gui.open(player)
    }
}
