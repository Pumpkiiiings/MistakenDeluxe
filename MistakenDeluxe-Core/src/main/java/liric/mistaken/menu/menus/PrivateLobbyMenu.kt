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
        val config = pumpking.lib.config.ConfigManager.getMenuConfig("private_lobby")
        val titleText = config.getString("title", "<dark_gray>Configuración de Partida Privada") ?: "<dark_gray>Configuración de Partida Privada"
        val startName = config.getString("items.start.name", "<green><bold>Iniciar Partida") ?: "<green><bold>Iniciar Partida"
        val startLoreRaw = config.getStringList("items.start.lore").ifEmpty { listOf("<gray>Inicia la partida forzosamente.") }
        
        val rulesName = config.getString("items.rules.name", "<gold><bold>Reglas de Juego") ?: "<gold><bold>Reglas de Juego"
        val rulesLoreRaw = config.getStringList("items.rules.lore").ifEmpty { listOf("<gray>Modifica las reglas de la partida.") }

        val mapName = config.getString("items.map.name", "<gold><bold>Selector de Mapa") ?: "<gold><bold>Selector de Mapa"
        val mapLoreRaw = config.getStringList("items.map.lore").ifEmpty { listOf("<gray>Elige el mapa a jugar.") }

        val modeName = config.getString("items.mode.name", "<gold><bold>Selector de Modo") ?: "<gold><bold>Selector de Modo"
        val modeLoreRaw = config.getStringList("items.mode.lore").ifEmpty { listOf("<gray>Elige el modo de juego.") }

        val playersName = config.getString("items.players.name", "<gold><bold>Selector de Jugadores") ?: "<gold><bold>Selector de Jugadores"
        val playersLoreRaw = config.getStringList("items.players.lore").ifEmpty { listOf("<gray>Elige roles de jugadores.") }

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
                RuleEditorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(13, rulesItem)

        // Boton Mapa
        val mapItem = ItemBuilder.from(Material.MAP)
            .name(ColorTranslator.translate("<!italic>$mapName"))
            .lore(mapLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                MapSelectorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(15, mapItem)

        // Boton Modo
        val modeItem = ItemBuilder.from(Material.DIAMOND_SWORD)
            .name(ColorTranslator.translate("<!italic>$modeName"))
            .lore(modeLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                ModeSelectorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(17, modeItem)

        // Boton Jugadores
        val playersItem = ItemBuilder.from(Material.PLAYER_HEAD)
            .name(ColorTranslator.translate("<!italic>$playersName"))
            .lore(playersLoreRaw.map { ColorTranslator.translate("<!italic>$it") })
            .asGuiItem {
                PlayerSelectorMenu(plugin, session).abrir(player)
            }
        
        gui.setItem(19, playersItem)

        gui.open(player)
    }
}
