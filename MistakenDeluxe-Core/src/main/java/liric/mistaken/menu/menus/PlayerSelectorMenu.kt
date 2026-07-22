package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.PrivateGameSettings
import org.bukkit.Material
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator

class PlayerSelectorMenu(private val plugin: Mistaken, private val session: GameSession) {

    fun abrir(player: Player) {
        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic><dark_gray>Selector de Jugadores"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val settings = session.settings ?: PrivateGameSettings().also { session.settings = it }

        var slot = 10
        for (sessionPlayer in session.getPlayers()) {
            val name = sessionPlayer.name
            
            val isKiller = settings.allowedKillers.contains(name)
            val isSurvivor = settings.allowedSurvivors.contains(name)
            
            var roleText = "<gray>Aleatorio"
            var mat = Material.PLAYER_HEAD
            
            if (isKiller) {
                roleText = "<red><bold>Asesino"
                mat = Material.ZOMBIE_HEAD
            } else if (isSurvivor) {
                roleText = "<green><bold>Superviviente"
                mat = Material.APPLE
            }

            val item = ItemBuilder.from(mat)
                .name(ColorTranslator.translate("<!italic><yellow>$name"))
                .lore(
                    ColorTranslator.translate("<!italic><gray>Rol: $roleText"),
                    net.kyori.adventure.text.Component.empty(),
                    ColorTranslator.translate("<!italic><e>Click Izq: Toggle Asesino"),
                    ColorTranslator.translate("<!italic><e>Click Der: Toggle Superviviente")
                )
                .asGuiItem { event ->
                    if (event.isLeftClick) {
                        if (isKiller) {
                            settings.allowedKillers.remove(name)
                        } else {
                            settings.allowedKillers.add(name)
                            settings.allowedSurvivors.remove(name)
                        }
                        abrir(player)
                    } else if (event.isRightClick) {
                        if (isSurvivor) {
                            settings.allowedSurvivors.remove(name)
                        } else {
                            settings.allowedSurvivors.add(name)
                            settings.allowedKillers.remove(name)
                        }
                        abrir(player)
                    }
                }

            gui.setItem(slot++, item)
            if (slot == 17) slot = 19
            if (slot > 25) break // Max players shown
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
