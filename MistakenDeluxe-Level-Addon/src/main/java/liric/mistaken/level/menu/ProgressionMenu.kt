package liric.mistaken.level.menu

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.level.LevelAddonPlugin
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag

class ProgressionMenu(private val plugin: LevelAddonPlugin) {

    fun open(player: Player) {
        val config = plugin.menuConfig.config.getConfigurationSection("progression_menu") ?: return

        val titleText = config.getString("title", "<gold>Level Progression")!!
        val size = config.getInt("size", 54)

        val gui = Gui.gui()
            .title(MiniMessage.miniMessage().deserialize(titleText))
            .rows(size / 9)
            .create()

        gui.setDefaultClickAction { event -> event.isCancelled = true }

        val nodesConfig = config.getConfigurationSection("nodes")
        val currentLevel = plugin.manager.getLevel(player.uniqueId)

        if (nodesConfig != null) {
            for (key in nodesConfig.getKeys(false)) {
                // Key e.g., "level_10"
                val levelStr = key.replace("level_", "")
                val levelTarget = levelStr.toIntOrNull() ?: continue
                val slot = nodesConfig.getInt(key)

                val isUnlocked = currentLevel >= levelTarget

                val matStr = if (isUnlocked) {
                    config.getString("styles.unlocked.material", "LIME_DYE")!!
                } else {
                    config.getString("styles.locked.material", "GRAY_DYE")!!
                }
                
                val mat = Material.matchMaterial(matStr.uppercase()) ?: Material.GRAY_DYE
                
                val nameTemplate = if (isUnlocked) {
                    config.getString("styles.unlocked.name", "<green>Level $levelTarget")!!
                } else {
                    config.getString("styles.locked.name", "<red>Level $levelTarget")!!
                }

                val item = ItemBuilder.from(mat)
                    .name(MiniMessage.miniMessage().deserialize(nameTemplate))
                    .flags(*ItemFlag.entries.toTypedArray())
                    .asGuiItem()

                gui.setItem(slot, item)
            }
        }

        gui.open(player)
    }
}
