package liric.mistaken.utils

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.GuiItem
import liric.mistaken.Mistaken
import liric.mistaken.utils.color.ColorTranslator

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

object MenuUtils {

    /**
     * @deprecated Kept for compatibility. MiniPlaceholders is now handled at Component generation.
     */
    fun setPlaceholders(player: Player?, text: String): String {
        return text
    }

    /**
     * Backward compatibility wrapper for old menus.
     */
    fun createConfigItem(config: org.bukkit.configuration.file.FileConfiguration, basePath: String, defaultMat: Material, player: Player? = null): dev.triumphteam.gui.builder.item.BaseItemBuilder<*> {
        val section = config.getConfigurationSection(basePath)
        return if (section != null) {
            createItemBuilder(section, player, defaultMat)
        } else {
            ItemBuilder.from(defaultMat)
        }
    }

    /**
     * Creates an ItemBuilder parsing material, name, lore, custom-model-data, glow, amount, head-owner, head-texture.
     */
    fun createItemBuilder(
        section: ConfigurationSection,
        player: Player? = null,
        defaultMat: Material = Material.AIR
    ): dev.triumphteam.gui.builder.item.BaseItemBuilder<*> {
        val matStr = section.getString("material")
        val isHead = matStr?.equals("PLAYER_HEAD", ignoreCase = true) == true
        val mat = if (matStr != null) {
            runCatching { Material.valueOf(matStr.uppercase()) }.getOrDefault(defaultMat)
        } else {
            defaultMat
        }

        val builder = if (isHead) ItemBuilder.skull() else ItemBuilder.from(mat)

        // Parse Name
        val nameRaw = section.getString("name")
        if (nameRaw != null) {
            builder.name(ColorTranslator.translate(player, "<!italic>$nameRaw"))
        }

        // Parse Lore
        val loreRaw = section.getStringList("lore")
        if (loreRaw.isNotEmpty()) {
            val loreParsed = loreRaw.map { ColorTranslator.translate(player, "<!italic>$it") }
            builder.lore(loreParsed)
        }

        // Parse Model Data
        val modelData = section.getInt("custom-model-data", section.getInt("model_data", -1))
        if (modelData != -1) {
            builder.model(modelData)
        }

        // Parse Glow
        if (section.getBoolean("glow", false)) {
            builder.glow(true)
        }

        // Parse Amount
        val amount = section.getInt("amount", 1)
        if (amount > 1) {
            builder.amount(amount)
        }

        // Parse Heads (Base64 texture or Owner name)
        if (isHead) {
            val texture = section.getString("head-texture")
            if (!texture.isNullOrEmpty()) {
                (builder as dev.triumphteam.gui.builder.item.SkullBuilder).texture(texture)
            } else {
                val owner = section.getString("head-owner")
                if (!owner.isNullOrEmpty()) {
                    val parsedOwner = setPlaceholders(player, owner)
                    (builder as dev.triumphteam.gui.builder.item.SkullBuilder).owner(Bukkit.getOfflinePlayer(parsedOwner))
                } else if (player != null) {
                    // Fallback to the player opening the menu
                    (builder as dev.triumphteam.gui.builder.item.SkullBuilder).owner(player)
                }
            }
        }
        
        return builder
    }

    /**
     * Creates a fully-featured GuiItem and attaches click commands.
     */
    fun createGuiItem(
        section: ConfigurationSection,
        player: Player? = null,
        defaultMat: Material = Material.AIR
    ): GuiItem {
        val builder = createItemBuilder(section, player, defaultMat)
        val commands = section.getStringList("commands")
        return builder.asGuiItem { event ->
            if (commands.isNotEmpty()) {
                event.isCancelled = true // Standard protection
                val clicker = event.whoClicked as? Player ?: return@asGuiItem
                for (cmd in commands) {
                    val parsedCmd = setPlaceholders(clicker, cmd)
                    if (parsedCmd.startsWith("[console] ")) {
                        val realCmd = parsedCmd.removePrefix("[console] ").trim()
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), realCmd)
                    } else if (parsedCmd.startsWith("[player] ")) {
                        val realCmd = parsedCmd.removePrefix("[player] ").trim()
                        clicker.chat("/$realCmd") // Forces player to execute command
                    } else if (parsedCmd.startsWith("[close]")) {
                        clicker.closeInventory()
                    }
                }
            }
        }
    }

    /**
     * Applies the 'overlay' and 'overlay_items' matrix layout to a given GUI.
     */
    fun applyOverlay(gui: dev.triumphteam.gui.guis.BaseGui, section: ConfigurationSection, player: Player?) {
        val overlayList = section.getStringList("overlay")
        if (overlayList.isNotEmpty()) {
            val overlayItemsSection = section.getConfigurationSection("overlay_items")
            if (overlayItemsSection != null) {
                val overlayMap = mutableMapOf<Char, GuiItem>()
                // Create items for each character
                overlayItemsSection.getKeys(false).forEach { charKey ->
                    if (charKey.isNotEmpty()) {
                        val c = charKey[0]
                        val itemSection = overlayItemsSection.getConfigurationSection(charKey)
                        if (itemSection != null) {
                            overlayMap[c] = createGuiItem(itemSection, player)
                        }
                    }
                }

                // Parse the rows
                for (row in overlayList.indices) {
                    val rowString = overlayList[row]
                    for (col in rowString.indices) {
                        if (col >= 9) break // Max 9 columns
                        val c = rowString[col]
                        if (c == ' ') continue
                        
                        val guiItem = overlayMap[c]
                        if (guiItem != null) {
                            val slot = (row * 9) + col
                            gui.setItem(slot, guiItem)
                        }
                    }
                }
            }
        }
    }
}
