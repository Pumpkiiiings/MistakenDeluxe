package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.PrivateGameSettings
import org.bukkit.Material
import org.bukkit.entity.Player
import liric.mistaken.utils.color.ColorTranslator

class MapSelectorMenu(private val plugin: Mistaken, private val session: GameSession) {

    fun abrir(player: Player) {
        val config = liric.mistaken.config.engine.core.ConfigManager.getMenuConfig("private_lobby")
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

        val section = config.getConfigurationSection("menus.map_selector")
        if (section != null) {
            liric.mistaken.utils.MenuUtils.applyOverlay(gui, section, player)
        }

        val settings = session.settings ?: PrivateGameSettings().also { session.settings = it }

        val allMaps = plugin.arenaManager.getArenas()

        var slot = startSlot
        for (map in allMaps) {
            val isSelected = settings.forcedMap == map.name
            val defaultMat = if (isSelected) Material.MAP else Material.PAPER
            val color = if (isSelected) "<green><bold>" else "<yellow>"
            
            val itemSection = config.getConfigurationSection("menus.map_selector.items.map")
            val builder = if (itemSection != null) liric.mistaken.utils.MenuUtils.createItemBuilder(itemSection, player, defaultMat) else ItemBuilder.from(defaultMat)
            
            val item = builder
                .name(ColorTranslator.translate("<!italic>$color${map.name}"))
                .lore(
                    ColorTranslator.translate("<!italic>${loreId.replace("{map}", map.name)}"),
                    net.kyori.adventure.text.Component.empty(),
                    ColorTranslator.translate("<!italic>$loreClick")
                )
                .asGuiItem {
                    settings.forcedMap = if (isSelected) null else map.name
                    player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                    player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.map_selector.messages.map_selected", "<green>Mapa selected: {map}", "messages").replace("{map}", settings.forcedMap ?: "AUTOMÁTICO")))
                    abrir(player)
                }

            gui.setItem(slot++, item)
            
            if (slot == 26) slot = 28 
            
            if (slot > maxSlots) break 
        }

        val backNameFallback = config.getString("menus.private_lobby.items.back.name", "<red>Volver") ?: "<red>Volver"
        val backNameFinal = config.getString("menus.map_selector.items.back.name", backNameFallback) ?: backNameFallback
        
        val backSection = config.getConfigurationSection("menus.map_selector.items.back")
        val builderBack = if (backSection != null) liric.mistaken.utils.MenuUtils.createItemBuilder(backSection, player, Material.ARROW) else ItemBuilder.from(Material.ARROW)
        
        gui.setItem(backSlot, builderBack
            .name(ColorTranslator.translate("<!italic>$backNameFinal"))
            .asGuiItem {
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 0.8f)
                PrivateLobbyMenu(plugin, session).abrir(player)
            })

        gui.open(player)
    }
}
