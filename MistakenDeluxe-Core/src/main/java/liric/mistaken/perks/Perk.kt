package liric.mistaken.perks

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.utils.color.ColorTranslator
import dev.triumphteam.gui.builder.item.ItemBuilder

abstract class Perk(
    val id: String,
    val icon: ItemStack
) {
    /** Gets the display name for this perk for a given player (localized) */
    fun getDisplayName(player: Player?): String {
        return MessageService.getRawString(player, "perks.$id.name", id, "messages")
    }

    /** Gets the description for this perk for a given player (localized) */
    fun getDescription(player: Player?): String {
        return MessageService.getRawString(player, "perks.$id.desc", "", "messages")
    }

    /** Builds a reveal GUI item showing the perk's name and description */
    fun buildRevealItem(player: Player): dev.triumphteam.gui.guis.GuiItem {
        val name = getDisplayName(player)
        val desc = getDescription(player)
        return ItemBuilder.from(icon.type)
            .name(ColorTranslator.translate(name))
            .lore(
                ColorTranslator.translate(" "),
                ColorTranslator.translate(desc),
                ColorTranslator.translate(" ")
            )
            .asGuiItem()
    }

    open fun onGeneratorRepair(player: Player) {}
    open fun onMove(player: Player) {}
    open fun onLookedAtByKiller(player: Player) {}
    open fun onDamaged(player: Player) {}
    open fun onEquip(player: Player) {}
    open fun onUnequip(player: Player) {}
}
