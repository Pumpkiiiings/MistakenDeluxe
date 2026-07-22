package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.PrivateGameSettings
import org.bukkit.Material
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator

class MapSelectorMenu(private val plugin: Mistaken, private val session: GameSession) {

    fun abrir(player: Player) {
        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic><dark_gray>Seleccionar Mapa"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val settings = session.settings ?: PrivateGameSettings().also { session.settings = it }

        // Fetch all available maps from plugin.arenaManager
        val allMaps = plugin.arenaManager.getArenas().values

        var slot = 10
        for (map in allMaps) {
            val isSelected = settings.forcedMap == map.name
            val mat = if (isSelected) Material.MAP else Material.PAPER
            val color = if (isSelected) "<green><bold>" else "<yellow>"
            
            val item = ItemBuilder.from(mat)
                .name(ColorTranslator.translate("<!italic>$color${map.name}"))
                .lore(
                    ColorTranslator.translate("<!italic><gray>ID: ${map.name}"),
                    net.kyori.adventure.text.Component.empty(),
                    ColorTranslator.translate("<!italic><e>Click para seleccionar")
                )
                .asGuiItem {
                    settings.forcedMap = if (isSelected) null else map.name
                    abrir(player)
                }

            gui.setItem(slot++, item)
            if (slot == 17) slot = 19
            if (slot > 25) break // Max maps shown
        }

        gui.setItem(31, ItemBuilder.from(Material.ARROW)
            .name(ColorTranslator.translate("<!italic><red>Volver"))
            .asGuiItem {
                gui.close(player)
                PrivateLobbyMenu(plugin, session).abrir(player)
            })

        gui.open(player)
    }
}
