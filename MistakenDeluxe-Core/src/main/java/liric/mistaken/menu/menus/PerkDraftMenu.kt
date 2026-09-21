package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.perks.Perk
import liric.mistaken.utils.color.ColorTranslator
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * PerkDraftMenu — shown once per game during the STARTING countdown.
 * Players pick 1 perk from 3 random options. Only active for that match.
 * All text is read from messages.yml under the `perks.*` section.
 */
class PerkDraftMenu(private val plugin: Mistaken) {

    fun open(player: Player, options: List<Perk>) {
        val title = MessageService.getRawString(player, "perks.draft-title", "<#3BFFC7>Bonus Perk | Choose 1", "messages")

        val gui = Gui.gui()
            .title(ColorTranslator.translate(title))
            .rows(3)
            .disableAllInteractions()
            .create()

        val bg = ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE).name(ColorTranslator.translate(" ")).asGuiItem()
        gui.filler.fill(bg)

        // 3 mystery cards in slots 11, 13, 15
        val displaySlots = listOf(11, 13, 15)
        val cardName = MessageService.getRawString(player, "perks.draft-card-name", "<#FFE300><bold>❓ Mystery Perk", "messages")
        val cardLore1 = MessageService.getRawString(player, "perks.draft-card-lore-1", "<gray>Click to reveal and select.", "messages")
        val cardLore2 = MessageService.getRawString(player, "perks.draft-card-lore-2", " ", "messages")
        val cardLore3 = MessageService.getRawString(player, "perks.draft-card-lore-3", "<dark_gray>Lasts this match only.", "messages")

        options.take(3).forEachIndexed { index, perk ->
            val item = ItemBuilder.from(Material.FILLED_MAP)
                .name(ColorTranslator.translate(cardName))
                .lore(
                    ColorTranslator.translate(cardLore1),
                    ColorTranslator.translate(cardLore2),
                    ColorTranslator.translate(cardLore3)
                )
                .asGuiItem { _ ->
                    if (plugin.perkManager.getActivePerk(player) != null) {
                        val alreadyMsg = MessageService.getRawString(player, "perks.already-selected", "<red>Already have a perk!", "messages")
                        player.sendMessage(ColorTranslator.translate(alreadyMsg))
                        return@asGuiItem
                    }
                    plugin.perkManager.assignPerk(player.uniqueId, perk)
                    player.closeInventory()
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)

                    val perkName = MessageService.getRawString(player, "perks.${perk.id}.name", perk.id, "messages")
                    val selectedMsg = MessageService.getRawString(player, "perks.selected", "<#3BFFC7>✦ Perk | {perk} equipped!", "messages")
                        .replace("{perk}", perkName)
                    val actionbarMsg = MessageService.getRawString(player, "perks.actionbar-active", "<#3BFFC7>✦ Perk: {perk}", "messages")
                        .replace("{perk}", perkName)
                    player.sendMessage(ColorTranslator.translate(selectedMsg))
                    player.sendActionBar(ColorTranslator.translate(actionbarMsg))
                }
            gui.setItem(displaySlots[index], item)
        }

        // Info item in bottom center (slot 22)
        val infoName = MessageService.getRawString(player, "perks.draft-info-name", "<white>How it works", "messages")
        val infoLore1 = MessageService.getRawString(player, "perks.draft-info-lore-1", "<gray>Pick 1 mystery card.", "messages")
        val infoLore2 = MessageService.getRawString(player, "perks.draft-info-lore-2", "<gray>Your perk will be revealed on selection.", "messages")
        val infoLore3 = MessageService.getRawString(player, "perks.draft-info-lore-3", "<gray>It lasts for this match only.", "messages")

        val info = ItemBuilder.from(Material.BOOK)
            .name(ColorTranslator.translate(infoName))
            .lore(
                ColorTranslator.translate(infoLore1),
                ColorTranslator.translate(infoLore2),
                ColorTranslator.translate(infoLore3)
            )
            .asGuiItem()
        gui.setItem(22, info)

        gui.open(player)

        // Auto-assign if idle after 30 seconds
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (gui.inventory.viewers.contains(player)) {
                player.closeInventory()
            }
            if (plugin.perkManager.getActivePerk(player) == null && options.isNotEmpty()) {
                plugin.perkManager.assignPerk(player.uniqueId, options.random())
                val autoMsg = MessageService.getRawString(player, "perks.auto-assigned", "<gray>✦ Perk auto-assigned.", "messages")
                player.sendMessage(ColorTranslator.translate(autoMsg))
            }
        }, 30 * 20L)
    }
}
