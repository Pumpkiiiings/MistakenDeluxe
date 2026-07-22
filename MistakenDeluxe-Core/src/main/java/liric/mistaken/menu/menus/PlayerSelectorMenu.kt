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
        val config = pumpking.lib.config.ConfigManager.getMenuConfig("private_lobby")
        val title = config.getString("menus.player_selector.title", "<dark_gray>Selector de Jugadores") ?: "<dark_gray>Selector de Jugadores"
        val loreRole = config.getString("menus.player_selector.items.player.lore_role", "<gray>Rol: {role}") ?: "<gray>Rol: {role}"
        val loreLeft = config.getString("menus.player_selector.items.player.lore_left", "<e>Click Izq: Toggle Asesino") ?: "<e>Click Izq: Toggle Asesino"
        val loreRight = config.getString("menus.player_selector.items.player.lore_right", "<e>Click Der: Toggle Superviviente") ?: "<e>Click Der: Toggle Superviviente"
        val roleRandom = config.getString("menus.player_selector.items.player.roles.random", "<gray>Aleatorio") ?: "<gray>Aleatorio"
        val roleKiller = config.getString("menus.player_selector.items.player.roles.killer", "<red><bold>Asesino") ?: "<red><bold>Asesino"
        val roleSurvivor = config.getString("menus.player_selector.items.player.roles.survivor", "<green><bold>Superviviente") ?: "<green><bold>Superviviente"
        val backName = config.getString("items.back.name", "<red>Volver") ?: "<red>Volver"

        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic>$title"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val settings = session.settings ?: PrivateGameSettings().also { session.settings = it }

        var slot = 10
        for (sessionPlayer in session.getPlayers()) {
            val name = sessionPlayer.name
            
            val isKiller = settings.allowedKillers.contains(name)
            val isSurvivor = settings.allowedSurvivors.contains(name)
            
            var roleText = roleRandom
            var mat = Material.PLAYER_HEAD
            
            if (isKiller) {
                roleText = roleKiller
                mat = Material.ZOMBIE_HEAD
            } else if (isSurvivor) {
                roleText = roleSurvivor
                mat = Material.APPLE
            }

            val item = ItemBuilder.from(mat)
                .name(ColorTranslator.translate("<!italic><yellow>$name"))
                .lore(
                    ColorTranslator.translate("<!italic>${loreRole.replace("{role}", roleText)}"),
                    net.kyori.adventure.text.Component.empty(),
                    ColorTranslator.translate("<!italic>$loreLeft"),
                    ColorTranslator.translate("<!italic>$loreRight")
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
            .name(ColorTranslator.translate("<!italic>$backName"))
            .asGuiItem {
                PrivateLobbyMenu(plugin, session).abrir(player)
            })

        gui.open(player)
    }
}
