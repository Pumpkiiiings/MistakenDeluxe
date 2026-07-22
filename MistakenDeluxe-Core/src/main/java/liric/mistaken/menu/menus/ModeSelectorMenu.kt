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
        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic><dark_gray>Seleccionar Modo"))
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
                    ColorTranslator.translate("<!italic><e>Click para seleccionar")
                )
                .asGuiItem {
                    settings.forcedMode = if (isSelected) null else mode
                    abrir(player)
                }

            gui.setItem(slot++, item)
            if (slot == 16) break
        }

        gui.setItem(22, ItemBuilder.from(Material.ARROW)
            .name(ColorTranslator.translate("<!italic><red>Volver"))
            .asGuiItem {
                gui.close(player)
                PrivateLobbyMenu(plugin, session).abrir(player)
            })

        gui.open(player)
    }
}
