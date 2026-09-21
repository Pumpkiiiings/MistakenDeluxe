package liric.mistaken.menu

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import liric.mistaken.Mistaken
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.ConfigManager
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.utils.MenuUtils

abstract class MenuBase(
    private val menuName: String
) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    protected fun parseSafe(text: String): Component {
        return ColorTranslator.translate("<!italic>$text")
    }

    protected open val titleMessageKey: String get() = "menus.$menuName.title"
    protected open val titleFallback: String get() = "<red>Menu: $menuName"

    private var globalConfig: FileConfiguration? = null

    fun getGlobalConfig(): FileConfiguration {
        return globalConfig ?: loadGlobalConfig().also { globalConfig = it }
    }

    open fun abrir(player: Player) {
        val config = getGlobalConfig()
        
        val rawTitle = MessageService.getRawString(player, titleMessageKey, titleFallback, "messages")
        val parsedTitle = MenuUtils.setPlaceholders(player, rawTitle)
        
        val filas = config.getInt("rows", 3)

        val gui = Gui.gui()
            .title(ColorTranslator.translate(parsedTitle))
            .rows(filas)
            .disableAllInteractions()
            .create()

        // --- DECORATIONS ---
        val decorSection = config.getConfigurationSection("decorations")
        decorSection?.getKeys(false)?.forEach { key ->
            val section = decorSection.getConfigurationSection(key)
            if (section != null) {
                val slots = section.getIntegerList("slots")
                if (slots.isNotEmpty()) {
                    val item = MenuUtils.createGuiItem(section, player)
                    gui.setItem(slots, item)
                }
            }
        }

        // --- OVERLAY SUPPORT ---
        MenuUtils.applyOverlay(gui, config, player)

        setupItems(player, gui, config)

        gui.open(player)
    }

    abstract fun setupItems(player: Player, gui: Gui, config: FileConfiguration)

    fun getTranslatedString(player: Player, path: String, def: String = "<red>Missing: $path"): String {
        return MessageService.getRawString(player, path, def, "messages")
    }

    fun getTranslatedList(player: Player, path: String): List<String> {
        return MessageService.getRawStringList(player, path, "messages")
    }

    fun reload() {
        globalConfig = null
    }

    private fun loadGlobalConfig(): FileConfiguration {
        return ConfigManager.getMenuConfig(menuName)
    }
}
