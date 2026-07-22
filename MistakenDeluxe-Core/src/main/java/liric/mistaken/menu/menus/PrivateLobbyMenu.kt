package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.config.Messages
import org.bukkit.Material
import org.bukkit.entity.Player
import pumpking.lib.service.PumpkingServiceManager
import pumpking.lib.color.ColorTranslator

class PrivateLobbyMenu(private val plugin: Mistaken, private val session: GameSession) {

    fun abrir(player: Player) {
        val titleText = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_LOBBY_TITLE, "<dark_gray>Configuración de Partida Privada", "messages")
        val startName = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_LOBBY_START_NAME, "<green><bold>Iniciar Partida", "messages")
        val startLoreRaw = PumpkingServiceManager.messages.getStrictStringList(player, Messages.MENUS_PRIVATE_LOBBY_START_LORE, "messages").ifEmpty { listOf("<gray>Inicia la partida forzosamente.") }
        
        val rulesName = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_LOBBY_RULES_NAME, "<gold><bold>Reglas de Juego", "messages")
        val rulesLoreRaw = PumpkingServiceManager.messages.getStrictStringList(player, Messages.MENUS_PRIVATE_LOBBY_RULES_LORE, "messages").ifEmpty { listOf("<gray>Modifica las reglas de la partida.") }

        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic>$titleText"))
            .rows(3)
            .disableAllInteractions()
            .create()

        // Boton Iniciar
        val startItem = ItemBuilder.from(Material.EMERALD_BLOCK)
            .name(ColorTranslator.translate("<!italic>$startName"))
            .lore(startLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                gui.close(player)
                if (session.isPrivate) {
                    session.forceStart = true
                    player.sendMessage("§aIniciando partida privada...")
                }
            }
        
        gui.setItem(11, startItem)

        // Boton Reglas
        val rulesItem = ItemBuilder.from(Material.COMPARATOR)
            .name(ColorTranslator.translate("<!italic>$rulesName"))
            .lore(rulesLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                gui.close(player)
                RuleEditorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(13, rulesItem)

        // Boton Mapa
        val mapItem = ItemBuilder.from(Material.MAP)
            .name(ColorTranslator.translate("<!italic><gold><bold>Selector de Mapa"))
            .lore(ColorTranslator.translate("<!italic><gray>Elige el mapa a jugar."))
            .asGuiItem {
                gui.close(player)
                MapSelectorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(15, mapItem)

        // Boton Modo
        val modeItem = ItemBuilder.from(Material.DIAMOND_SWORD)
            .name(ColorTranslator.translate("<!italic><gold><bold>Selector de Modo"))
            .lore(ColorTranslator.translate("<!italic><gray>Elige el modo de juego."))
            .asGuiItem {
                gui.close(player)
                ModeSelectorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(17, modeItem)

        // Boton Jugadores
        val playersItem = ItemBuilder.from(Material.PLAYER_HEAD)
            .name(ColorTranslator.translate("<!italic><gold><bold>Selector de Jugadores"))
            .lore(ColorTranslator.translate("<!italic><gray>Elige roles de jugadores."))
            .asGuiItem {
                gui.close(player)
                PlayerSelectorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(19, playersItem)

        gui.open(player)
    }
}
