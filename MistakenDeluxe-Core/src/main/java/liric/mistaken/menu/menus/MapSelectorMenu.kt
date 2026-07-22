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
        val config = pumpking.lib.config.ConfigManager.getMenuConfig("private_lobby")
        val title = config.getString("menus.map_selector.title", "<dark_gray>Seleccionar Mapa") ?: "<dark_gray>Seleccionar Mapa"
        val loreId = config.getString("menus.map_selector.items.map.lore_id", "<gray>ID: {map}") ?: "<gray>ID: {map}"
        val loreClick = config.getString("menus.map_selector.items.map.lore_click", "<e>Click para seleccionar") ?: "<e>Click para seleccionar"
        val backName = config.getString("items.back.name", "<red>Volver") ?: "<red>Volver"

        val rows = config.getInt("menus.map_selector.rows", 5)
        val fillerMatStr = config.getString("menus.map_selector.filler_material", "BLACK_STAINED_GLASS_PANE") ?: "BLACK_STAINED_GLASS_PANE"
        val fillerMat = runCatching { Material.valueOf(fillerMatStr.uppercase()) }.getOrDefault(Material.BLACK_STAINED_GLASS_PANE)
        
        val startSlot = config.getInt("menus.map_selector.start_slot", 19)
        val maxSlots = config.getInt("menus.map_selector.max_slots", 34)
        val backSlot = config.getInt("menus.map_selector.back_slot", 40)

        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic>$title"))
            .rows(rows)
            .disableAllInteractions()
            .create()

        if (fillerMat != Material.AIR) {
            val fillerItem = ItemBuilder.from(fillerMat)
                .name(ColorTranslator.translate(" "))
                .asGuiItem()
            gui.filler.fill(fillerItem)
        }

        val settings = session.settings ?: PrivateGameSettings().also { session.settings = it }

        // Fetch all available maps from plugin.arenaManager
        val allMaps = plugin.arenaManager.getArenas().values

        var slot = startSlot
        for (map in allMaps) {
            val isSelected = settings.forcedMap == map.name
            val mat = if (isSelected) Material.MAP else Material.PAPER
            val color = if (isSelected) "<green><bold>" else "<yellow>"
            
            val item = ItemBuilder.from(mat)
                .name(ColorTranslator.translate("<!italic>$color${map.name}"))
                .lore(
                    ColorTranslator.translate("<!italic>${loreId.replace("{map}", map.name)}"),
                    net.kyori.adventure.text.Component.empty(),
                    ColorTranslator.translate("<!italic>$loreClick")
                )
                .asGuiItem {
                    settings.forcedMap = if (isSelected) null else map.name
                    player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                    player.sendActionBar(ColorTranslator.translate("<green>Mapa seleccionado: ${settings.forcedMap ?: "AUTOMÁTICO"}"))
                    abrir(player)
                }

            gui.setItem(slot++, item)
            // Lógica simple de filas (salta a la siguiente fila si llega al borde derecho asumiendo centrado estándar)
            if (slot == 26) slot = 28 
            
            if (slot > maxSlots) break // Max maps shown
        }

        gui.setItem(backSlot, ItemBuilder.from(Material.ARROW)
            .name(ColorTranslator.translate("<!italic>$backName"))
            .asGuiItem {
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 0.8f)
                PrivateLobbyMenu(plugin, session).abrir(player)
            })

        gui.open(player)
    }
}
