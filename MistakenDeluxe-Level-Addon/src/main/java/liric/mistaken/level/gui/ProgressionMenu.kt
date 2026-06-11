package liric.mistaken.level.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.PaginatedGui
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.level.LevelAddonPlugin
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag

class ProgressionMenu(private val plugin: LevelAddonPlugin) {

    private val mm = MiniMessage.miniMessage()

    // 28 slots for a zig-zag path across a 6-row GUI
    // Row 2: 10, 11, 12, 13, 14, 15, 16
    // Row 3: 25, 24, 23, 22, 21, 20, 19
    // Row 4: 28, 29, 30, 31, 32, 33, 34
    // Row 5: 43, 42, 41, 40, 39, 38, 37
    private val zigzagSlots = listOf(
        10, 11, 12, 13, 14, 15, 16,
        25, 24, 23, 22, 21, 20, 19,
        28, 29, 30, 31, 32, 33, 34,
        43, 42, 41, 40, 39, 38, 37
    )

    fun open(player: Player) {
        val gui = Gui.paginated()
            .title(mm.deserialize("<dark_gray>Level Progression</dark_gray>"))
            .rows(6)
            .pageSize(28)
            .create()

        gui.setDefaultClickAction { event ->
            event.isCancelled = true
        }

        val playerLevel = plugin.manager.getLevel(player.uniqueId)
        val playerXp = plugin.manager.getExperience(player.uniqueId)

        // Borders
        val border = ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE).name(mm.deserialize("")).asGuiItem()
        gui.filler.fillBorder(border)

        // Navigation
        val previous = ItemBuilder.from(Material.ARROW).name(mm.deserialize("<green>Previous Page")).asGuiItem { gui.previous() }
        val next = ItemBuilder.from(Material.ARROW).name(mm.deserialize("<green>Next Page")).asGuiItem { gui.next() }
        gui.setItem(6, 3, previous)
        gui.setItem(6, 7, next)

        val maxLevel = plugin.levelConfig.maxLevel

        for (lvl in 1..maxLevel) {
            val material = getMaterialForLevel(lvl)
            val isCurrent = lvl == playerLevel
            val isCompleted = lvl < playerLevel
            val isLocked = lvl > playerLevel

            val itemBuilder = if (isCompleted) {
                ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                    .name(mm.deserialize("<green><bold>Level $lvl</bold></green>"))
                    .lore(mm.deserialize("<gray>✔ Completed</gray>"))
            } else if (isCurrent) {
                val reqXp = plugin.manager.getRequiredXp(lvl)
                val pct = if (reqXp > 0) (playerXp.toDouble() / reqXp.toDouble()) * 100 else 100.0
                val pctStr = String.format("%.1f", pct)
                val reward = plugin.levelConfig.getRewardForLevel(lvl)?.name ?: "None"
                
                ItemBuilder.from(material)
                    .name(mm.deserialize("<yellow><bold>Level $lvl</bold></yellow>"))
                    .lore(
                        mm.deserialize("<gray>Progress: <yellow>$playerXp / $reqXp <gray>($pctStr%)</gray>"),
                        mm.deserialize(""),
                        mm.deserialize("<white>Reward: <aqua>$reward</aqua>")
                    )
                    .enchant(Enchantment.UNBREAKING)
                    .flags(ItemFlag.HIDE_ENCHANTS)
            } else {
                val reward = plugin.levelConfig.getRewardForLevel(lvl)?.name ?: "Hidden"
                ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                    .name(mm.deserialize("<dark_gray>Level $lvl</dark_gray>"))
                    .lore(
                        mm.deserialize("<gray>Locked</gray>"),
                        mm.deserialize(""),
                        mm.deserialize("<dark_gray>Reward Preview: $reward</dark_gray>")
                    )
            }

            gui.addItem(itemBuilder.asGuiItem())
        }

        // Apply zigzag layout
        // Not using PageChangeAction since PaginatedGui auto-handles items

        // Wait, TriumphGui `PaginatedGui` places items sequentially in any available slots.
        // We can just use `Gui.paginated()` but manually restrict the slots!
        // We set the filler to fill all non-zigzag slots.
        val nonZigzagSlots = mutableListOf<Int>()
        for (i in 0..53) {
            if (!zigzagSlots.contains(i) && i != 47 && i != 51) { // 47 and 51 are prev/next
                nonZigzagSlots.add(i)
            }
        }
        gui.setItem(nonZigzagSlots, border)

        gui.open(player)
    }

    private fun getMaterialForLevel(level: Int): Material {
        return when (level) {
            10 -> Material.IRON_INGOT
            25 -> Material.GOLD_INGOT
            50 -> Material.DIAMOND
            75 -> Material.NETHERITE_INGOT
            100 -> Material.NETHER_STAR
            else -> Material.PAPER
        }
    }
}
