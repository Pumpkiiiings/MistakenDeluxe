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
        val titleText = config.getString("menus.private_lobby.title", "<dark_gray>Configuración de Partida Privada") ?: "<dark_gray>Configuración de Partida Privada"
        val startName = config.getString("menus.private_lobby.items.start.name", "<green><bold>Iniciar Partida") ?: "<green><bold>Iniciar Partida"
        val startLoreRaw = config.getStringList("menus.private_lobby.items.start.lore").ifEmpty { listOf("<gray>Inicia la partida forzosamente.") }
        
        val rulesName = config.getString("menus.private_lobby.items.rules.name", "<gold><bold>Reglas de Juego") ?: "<gold><bold>Reglas de Juego"
        val rulesLoreRaw = config.getStringList("menus.private_lobby.items.rules.lore").ifEmpty { listOf("<gray>Modifica las reglas de la partida.") }

        val mapName = config.getString("menus.private_lobby.items.map.name", "<gold><bold>Selector de Mapa") ?: "<gold><bold>Selector de Mapa"
        val mapLoreRaw = config.getStringList("menus.private_lobby.items.map.lore").ifEmpty { listOf("<gray>Elige el mapa a jugar.") }

        val modeName = config.getString("menus.private_lobby.items.mode.name", "<gold><bold>Selector de Modo") ?: "<gold><bold>Selector de Modo"
        val modeLoreRaw = config.getStringList("menus.private_lobby.items.mode.lore").ifEmpty { listOf("<gray>Elige el modo de juego.") }

        val playersName = config.getString("menus.private_lobby.items.players.name", "<gold><bold>Selector de Jugadores") ?: "<gold><bold>Selector de Jugadores"
        val playersLoreRaw = config.getStringList("menus.private_lobby.items.players.lore").ifEmpty { listOf("<gray>Elige roles de jugadores.") }

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

        if (fillerMat != Material.AIR) {
            val fillerItem = ItemBuilder.from(fillerMat)
                .name(ColorTranslator.translate(" "))
                .asGuiItem()
            gui.filler.fill(fillerItem)
        }

        
        val startItem = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.private_lobby.items.start", Material.EMERALD_BLOCK)
            .name(ColorTranslator.translate("<!italic>$startName"))
            .lore(startLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                gui.close(player)
                if (session.isPrivate) {
                    session.forceStart = true
                    player.sendMessage(ColorTranslator.translate("<green><bold>¡Iniciando partida privada!"))
                }
            }
        
        gui.setItem(startSlot, startItem)

        
        val rulesItem = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.private_lobby.items.rules", Material.COMPARATOR)
            .name(ColorTranslator.translate("<!italic>$rulesName"))
            .lore(rulesLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                RuleEditorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(rulesSlot, rulesItem)

        
        val mapItem = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.private_lobby.items.map", Material.MAP)
            .name(ColorTranslator.translate("<!italic>$mapName"))
            .lore(mapLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                MapSelectorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(mapSlot, mapItem)

        
        val modeItem = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.private_lobby.items.mode", Material.DIAMOND_SWORD)
            .name(ColorTranslator.translate("<!italic>$modeName"))
            .lore(modeLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                ModeSelectorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(modeSlot, modeItem)

        
        val playersItem = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.private_lobby.items.players", Material.PLAYER_HEAD)
            .name(ColorTranslator.translate("<!italic>$playersName"))
            .lore(playersLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                PlayerSelectorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(playersSlot, playersItem)

        gui.open(player)
    }
}
