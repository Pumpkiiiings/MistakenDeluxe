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

        val rows = config.getInt("menus.mode_selector.rows", 5)
        val fillerMatStr = config.getString("menus.mode_selector.filler_material", "BLACK_STAINED_GLASS_PANE") ?: "BLACK_STAINED_GLASS_PANE"
        val fillerMat = runCatching { Material.valueOf(fillerMatStr.uppercase()) }.getOrDefault(Material.BLACK_STAINED_GLASS_PANE)
        
        val startSlot = config.getInt("menus.mode_selector.start_slot", 20)
        val maxSlots = config.getInt("menus.mode_selector.max_slots", 24)
        val backSlot = config.getInt("menus.mode_selector.back_slot", 40)

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

        var slot = startSlot
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
                    player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                    player.sendActionBar(ColorTranslator.translate("<green>Modo seleccionado: ${settings.forcedMode?.name ?: "AUTOMÁTICO"}"))
                    abrir(player)
                }

            gui.setItem(slot++, item)
            if (slot > maxSlots) break
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
