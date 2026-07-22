package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.PrivateGameSettings
import org.bukkit.Material
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator

class CharacterSelectorMenu(private val plugin: Mistaken, private val session: GameSession) {

    fun abrir(player: Player) {
        val config = pumpking.lib.config.ConfigManager.getMenuConfig("private_lobby")
        val title = config.getString("menus.character_selector.title", "<dark_gray>Clases Permitidas") ?: "<dark_gray>Clases Permitidas"
        val loreState = config.getString("menus.character_selector.items.character.lore_state", "<gray>Estado: {color}{state}") ?: "<gray>Estado: {color}{state}"
        val loreClick = config.getString("menus.character_selector.items.character.lore_click", "<e>Click para alternar") ?: "<e>Click para alternar"
        val stateEnabled = config.getString("menus.character_selector.items.character.states.enabled", "<green>Permitido") ?: "<green>Permitido"
        val stateDisabled = config.getString("menus.character_selector.items.character.states.disabled", "<red>Bloqueado") ?: "<red>Bloqueado"
        val backName = config.getString("items.back.name", "<red>Volver") ?: "<red>Volver"

        val rows = config.getInt("menus.character_selector.rows", 6)
        val fillerMatStr = config.getString("menus.character_selector.filler_material", "BLACK_STAINED_GLASS_PANE") ?: "BLACK_STAINED_GLASS_PANE"
        val fillerMat = runCatching { Material.valueOf(fillerMatStr.uppercase()) }.getOrDefault(Material.BLACK_STAINED_GLASS_PANE)
        
        val startSlot = config.getInt("menus.character_selector.start_slot", 10)
        val maxSlots = config.getInt("menus.character_selector.max_slots", 43)
        val backSlot = config.getInt("menus.character_selector.back_slot", 49)

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
        val allKillers = plugin.asesinoManager.catalogo.values.toList()
        val allSurvivors = plugin.supervivienteManager.catalogo.values.toList()

        // For killers
        for (killer in allKillers) {
            val isEnabled = !settings.disabledClasses.contains(killer.id.lowercase())
            val color = if (isEnabled) "<green>" else "<red>"
            val stateText = if (isEnabled) stateEnabled else stateDisabled
            
            val item = ItemBuilder.from(Material.ZOMBIE_HEAD)
                .name(ColorTranslator.translate("<!italic><red>${killer.nombre}"))
                .lore(
                    ColorTranslator.translate("<!italic>${loreState.replace("{color}", color).replace("{state}", stateText)}"),
                    net.kyori.adventure.text.Component.empty(),
                    ColorTranslator.translate("<!italic>$loreClick")
                )
                .asGuiItem {
                    // Slasher cannot be disabled as it's the fallback
                    if (killer.id.equals("slasher", ignoreCase = true)) {
                        player.sendMessage(ColorTranslator.translate("<red>No puedes bloquear a Slasher, es la clase por defecto."))
                        return@asGuiItem
                    }
                    if (isEnabled) {
                        settings.disabledClasses.add(killer.id.lowercase())
                    } else {
                        settings.disabledClasses.remove(killer.id.lowercase())
                    }
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                    abrir(player)
                }
            
            if (slot <= maxSlots) {
                gui.setItem(slot++, item)
                if (slot % 9 == 8) slot += 2 // Skip edges assuming a standard layout
            }
        }

        // For survivors
        for (survivor in allSurvivors) {
            val isEnabled = !settings.disabledClasses.contains(survivor.id.lowercase())
            val color = if (isEnabled) "<green>" else "<red>"
            val stateText = if (isEnabled) stateEnabled else stateDisabled
            
            val item = ItemBuilder.from(Material.APPLE)
                .name(ColorTranslator.translate("<!italic><green>${survivor.nombre}"))
                .lore(
                    ColorTranslator.translate("<!italic>${loreState.replace("{color}", color).replace("{state}", stateText)}"),
                    net.kyori.adventure.text.Component.empty(),
                    ColorTranslator.translate("<!italic>$loreClick")
                )
                .asGuiItem {
                    // Civilian cannot be disabled
                    if (survivor.id.equals("civilian", ignoreCase = true) || survivor.id.equals("civil", ignoreCase = true)) {
                        player.sendMessage(ColorTranslator.translate("<red>No puedes bloquear a Civilian, es la clase por defecto."))
                        return@asGuiItem
                    }
                    if (isEnabled) {
                        settings.disabledClasses.add(survivor.id.lowercase())
                    } else {
                        settings.disabledClasses.remove(survivor.id.lowercase())
                    }
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                    abrir(player)
                }
            
            if (slot <= maxSlots) {
                gui.setItem(slot++, item)
                if (slot % 9 == 8) slot += 2
            }
        }

        // Back button
        val backItem = ItemBuilder.from(Material.ARROW)
            .name(ColorTranslator.translate("<!italic>$backName"))
            .asGuiItem {
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 0.8f)
                RuleEditorMenu(plugin, session).abrir(player)
            }
        gui.setItem(backSlot, backItem)

        gui.open(player)
    }
}
