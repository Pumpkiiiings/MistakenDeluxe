package liric.mistaken.level.menu

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.PaginatedGui
import liric.mistaken.level.LevelAddonPlugin
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import kotlin.math.max

class ProgressionMenu(private val plugin: LevelAddonPlugin) {

    private val mm = MiniMessage.miniMessage()

    fun open(player: Player) {
        val config = plugin.menuConfig.config.getConfigurationSection("progression_menu") ?: return

        val titleText = config.getString("title", "<gold>Level Progression")!!
        val size = config.getInt("size", 54)
        val paginationEnabled = config.getBoolean("pagination.enabled", true)
        val slots = config.getIntegerList("pagination.slots").ifEmpty { (10..43).toList() }

        val gui: PaginatedGui = Gui.paginated()
            .title(mm.deserialize(titleText))
            .rows(size / 9)
            .pageSize(slots.size)
            .create()

        gui.setDefaultClickAction { event -> event.isCancelled = true }

        val currentLevel = plugin.manager.getLevel(player.uniqueId)
        val mistakenCore = org.bukkit.Bukkit.getPluginManager().getPlugin("MistakenDeluxe-Core") as liric.mistaken.Mistaken
        val stats = mistakenCore.statsManager.getStats(player.uniqueId)

        val maxLevel = plugin.levelConfig.maxLevel

        for (i in 1..maxLevel) {
            val levelTarget = i
            val isUnlocked = currentLevel >= levelTarget

            val stylePrefix = if (isUnlocked) "styles.unlocked" else "styles.locked"
            val matStr = config.getString("$stylePrefix.material", "GRAY_DYE")!!
            val mat = Material.matchMaterial(matStr.uppercase()) ?: Material.GRAY_DYE
            val nameTemplate = config.getString("$stylePrefix.name", "<gray>Level {level}")!!
            val loreTemplate = config.getStringList("$stylePrefix.lore")

            val reqs = plugin.levelConfig.getRequirementsForLevel(levelTarget)
            val reqXp = reqs?.xp ?: plugin.manager.getRequiredXp(levelTarget)
            val reqKills = reqs?.kills ?: 0
            val reqWinsGlobal = reqs?.winsGlobal ?: 0
            val reqGens = reqs?.generatorsRepaired ?: 0

            val currentXp = plugin.manager.getExperience(player.uniqueId)
            val currentKills = stats.kills.get()
            val currentWinsGlobal = stats.totalWins
            val currentGens = stats.generatorsRepaired.get()

            fun replacePlaceholders(text: String): String {
                return text.replace("{level}", levelTarget.toString())
                    .replace("{exp_required}", reqXp.toString())
                    .replace("{exp_current}", currentXp.toString())
                    .replace("{exp_left}", max(0L, reqXp - currentXp).toString())
                    .replace("{kills_required}", reqKills.toString())
                    .replace("{kills_current}", currentKills.toString())
                    .replace("{kills_left}", max(0, reqKills - currentKills).toString())
                    .replace("{wins_global_required}", reqWinsGlobal.toString())
                    .replace("{wins_global_current}", currentWinsGlobal.toString())
                    .replace("{wins_global_left}", max(0, reqWinsGlobal - currentWinsGlobal).toString())
                    .replace("{generators_required}", reqGens.toString())
                    .replace("{generators_current}", currentGens.toString())
                    .replace("{generators_left}", max(0, reqGens - currentGens).toString())
            }

            val filteredLoreTemplate = loreTemplate.filter { line ->
                if (reqXp <= 0 && (line.contains("{exp_") || line.contains("Experiencia:"))) return@filter false
                if (reqKills <= 0 && (line.contains("{kills_") || line.contains("Kills:"))) return@filter false
                if (reqWinsGlobal <= 0 && (line.contains("{wins_global_") || line.contains("Victorias"))) return@filter false
                if (reqGens <= 0 && (line.contains("{generators_") || line.contains("Generadores"))) return@filter false
                true
            }

            val item = ItemBuilder.from(mat)
                .name(mm.deserialize(replacePlaceholders(nameTemplate)))
                .lore(filteredLoreTemplate.map { mm.deserialize(replacePlaceholders(it)) })
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem()

            gui.addItem(item)
        }

        if (paginationEnabled) {
            val prevSlot = config.getInt("pagination.buttons.previous_page.slot", 45)
            val prevMat = Material.matchMaterial(config.getString("pagination.buttons.previous_page.material", "ARROW")!!) ?: Material.ARROW
            val prevName = config.getString("pagination.buttons.previous_page.name", "<yellow>Previous Page")!!
            gui.setItem(prevSlot, ItemBuilder.from(prevMat).name(mm.deserialize(prevName)).asGuiItem {
                gui.previous()
            })

            val nextSlot = config.getInt("pagination.buttons.next_page.slot", 53)
            val nextMat = Material.matchMaterial(config.getString("pagination.buttons.next_page.material", "ARROW")!!) ?: Material.ARROW
            val nextName = config.getString("pagination.buttons.next_page.name", "<yellow>Next Page")!!
            gui.setItem(nextSlot, ItemBuilder.from(nextMat).name(mm.deserialize(nextName)).asGuiItem {
                gui.next()
            })
            
            // Map items to slots
            gui.filler.fillBorder(ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE).name(net.kyori.adventure.text.Component.empty()).asGuiItem())
        }

        gui.open(player)
    }
}
