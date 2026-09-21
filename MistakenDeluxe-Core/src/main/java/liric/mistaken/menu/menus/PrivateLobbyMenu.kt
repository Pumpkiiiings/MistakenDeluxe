package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.config.Messages
import org.bukkit.Material
import org.bukkit.entity.Player
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.utils.color.ColorTranslator

class PrivateLobbyMenu(private val plugin: Mistaken, private val session: GameSession) {

    fun abrir(player: Player) {
        val config = liric.mistaken.config.engine.core.ConfigManager.getMenuConfig("private_lobby")
        val titleText = config.getString("menus.private_lobby.title", "<dark_gray>Private Game Setup") ?: "<dark_gray>Private Game Setup"
        val startName = config.getString("menus.private_lobby.items.start.name", "<green><bold>Start Game") ?: "<green><bold>Start Game"
        val startLoreRaw = config.getStringList("menus.private_lobby.items.start.lore").ifEmpty { listOf("<gray>Force start the game.") }
        
        val rulesName = config.getString("menus.private_lobby.items.rules.name", "<gold><bold>Game Rules") ?: "<gold><bold>Game Rules"
        val rulesLoreRaw = config.getStringList("menus.private_lobby.items.rules.lore").ifEmpty { listOf("<gray>Modify game rules.") }

        val mapName = config.getString("menus.private_lobby.items.map.name", "<gold><bold>Map Selector") ?: "<gold><bold>Map Selector"
        val mapLoreRaw = config.getStringList("menus.private_lobby.items.map.lore").ifEmpty { listOf("<gray>Choose the map to play.") }

        val modeName = config.getString("menus.private_lobby.items.mode.name", "<gold><bold>Mode Selector") ?: "<gold><bold>Mode Selector"
        val modeLoreRaw = config.getStringList("menus.private_lobby.items.mode.lore").ifEmpty { listOf("<gray>Choose the game mode.") }

        val playersName = config.getString("menus.private_lobby.items.players.name", "<gold><bold>Player Selector") ?: "<gold><bold>Player Selector"
        val playersLoreRaw = config.getStringList("menus.private_lobby.items.players.lore").ifEmpty { listOf("<gray>Choose player roles.") }

        val rows = config.getInt("menus.private_lobby.rows", 5)
        val fillerMatStr = config.getString("menus.private_lobby.filler_material", "BLACK_STAINED_GLASS_PANE") ?: "BLACK_STAINED_GLASS_PANE"
        val fillerMat = runCatching { Material.valueOf(fillerMatStr.uppercase()) }.getOrDefault(Material.BLACK_STAINED_GLASS_PANE)

        val startSlot = config.getInt("menus.private_lobby.slots.start", 22)
        val rulesSlot = config.getInt("menus.private_lobby.slots.rules", 20)
        val mapSlot = config.getInt("menus.private_lobby.slots.map", 23)
        val modeSlot = config.getInt("menus.private_lobby.slots.mode", 24)
        val playersSlot = config.getInt("menus.private_lobby.slots.players", 21)

        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic>$titleText"))
            .rows(rows)
            .disableAllInteractions()
            .create()

        val section = config.getConfigurationSection("menus.private_lobby")
        if (section != null) {
            liric.mistaken.utils.MenuUtils.applyOverlay(gui, section, player)
        }

        val startSection = config.getConfigurationSection("menus.private_lobby.items.start")
        if (startSection != null) {
            val startItem = liric.mistaken.utils.MenuUtils.createItemBuilder(startSection, player, Material.EMERALD_BLOCK)
                .asGuiItem {
                    player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    gui.close(player)
                    if (session.isPrivate) {
                        session.forceStart = true
                        player.sendMessage(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.private_lobby.messages.start_game", "<green><bold>Iniciando partida privada!", "messages")))
                    }
                }
            gui.setItem(startSlot, startItem)
        }

        val rulesSection = config.getConfigurationSection("menus.private_lobby.items.rules")
        if (rulesSection != null) {
            val rulesItem = liric.mistaken.utils.MenuUtils.createItemBuilder(rulesSection, player, Material.COMPARATOR)
                .asGuiItem {
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                    RuleEditorMenu(plugin, session).abrir(player)
                }
            gui.setItem(rulesSlot, rulesItem)
        }

        val mapSection = config.getConfigurationSection("menus.private_lobby.items.map")
        if (mapSection != null) {
            val mapItem = liric.mistaken.utils.MenuUtils.createItemBuilder(mapSection, player, Material.MAP)
                .asGuiItem {
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                    MapSelectorMenu(plugin, session).abrir(player)
                }
            gui.setItem(mapSlot, mapItem)
        }

        val modeSection = config.getConfigurationSection("menus.private_lobby.items.mode")
        if (modeSection != null) {
            val modeItem = liric.mistaken.utils.MenuUtils.createItemBuilder(modeSection, player, Material.DIAMOND_SWORD)
                .asGuiItem {
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                    ModeSelectorMenu(plugin, session).abrir(player)
                }
            gui.setItem(modeSlot, modeItem)
        }

        val playersSection = config.getConfigurationSection("menus.private_lobby.items.players")
        if (playersSection != null) {
            val playersItem = liric.mistaken.utils.MenuUtils.createItemBuilder(playersSection, player, Material.PLAYER_HEAD)
                .asGuiItem {
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                    PlayerSelectorMenu(plugin, session).abrir(player)
                }
            gui.setItem(playersSlot, playersItem)
        }

        gui.open(player)
    }
}
