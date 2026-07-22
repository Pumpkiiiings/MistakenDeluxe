package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.PrivateGameSettings
import liric.mistaken.config.Messages
import org.bukkit.Material
import org.bukkit.entity.Player
import pumpking.lib.service.PumpkingServiceManager
import pumpking.lib.color.ColorTranslator

class RuleEditorMenu(private val plugin: Mistaken, private val session: GameSession) {

    fun abrir(player: Player) {
        val config = pumpking.lib.config.ConfigManager.getMenuConfig("private_lobby")
        val titleText = config.getString("menus.rule_editor.title", "<dark_gray>Editor de Reglas") ?: "<dark_gray>Editor de Reglas"
        val backName = config.getString("items.back.name", "<red>Volver") ?: "<red>Volver"
        
        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic>$titleText"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val settings = session.settings ?: PrivateGameSettings().also { session.settings = it }

        // Glowing
        val glowingMat = if (settings.glowingEnabled) Material.GLOWSTONE_DUST else Material.GUNPOWDER
        val glowingColor = if (settings.glowingEnabled) "<green>" else "<red>"
        val glowingName = config.getString("menus.rule_editor.items.glowing.name", "<gold><bold>Glowing Constante") ?: "<gold><bold>Glowing Constante"
        val glowingLore = config.getString("menus.rule_editor.items.glowing.lore_state", "<gray>Estado: {color}{state}") ?: "<gray>Estado: {color}{state}"
        gui.setItem(10, ItemBuilder.from(glowingMat)
            .name(ColorTranslator.translate("<!italic>$glowingName"))
            .lore(ColorTranslator.translate("<!italic>${glowingLore.replace("{color}", glowingColor).replace("{state}", settings.glowingEnabled.toString())}"))
            .asGuiItem {
                settings.glowingEnabled = !settings.glowingEnabled
                abrir(player) // Refresh
            })

        // Heartbeats
        val hbMat = if (settings.heartbeatsEnabled != false) Material.NOTE_BLOCK else Material.JUKEBOX
        val hbColor = if (settings.heartbeatsEnabled != false) "<green>" else "<red>"
        val hbName = config.getString("menus.rule_editor.items.heartbeats.name", "<gold><bold>Latidos (Heartbeats)") ?: "<gold><bold>Latidos (Heartbeats)"
        val hbLore = config.getString("menus.rule_editor.items.heartbeats.lore_state", "<gray>Estado: {color}{state}") ?: "<gray>Estado: {color}{state}"
        gui.setItem(11, ItemBuilder.from(hbMat)
            .name(ColorTranslator.translate("<!italic>$hbName"))
            .lore(ColorTranslator.translate("<!italic>${hbLore.replace("{color}", hbColor).replace("{state}", (settings.heartbeatsEnabled != false).toString())}"))
            .asGuiItem {
                settings.heartbeatsEnabled = (settings.heartbeatsEnabled == false)
                abrir(player)
            })

        // Speed
        val speedVal = settings.speedMultiplier ?: 0
        val speedName = config.getString("menus.rule_editor.items.speed.name", "<gold><bold>Velocidad Base") ?: "<gold><bold>Velocidad Base"
        val speedLore = config.getString("menus.rule_editor.items.speed.lore_level", "<gray>Nivel: <yellow>{level}") ?: "<gray>Nivel: <yellow>{level}"
        gui.setItem(12, ItemBuilder.from(Material.SUGAR)
            .name(ColorTranslator.translate("<!italic>$speedName"))
            .lore(ColorTranslator.translate("<!italic>${speedLore.replace("{level}", speedVal.toString())}"))
            .asGuiItem {
                settings.speedMultiplier = if (speedVal >= 3) null else speedVal + 1
                abrir(player)
            })

        // Jump
        val jumpVal = settings.jumpMultiplier ?: 0
        val jumpName = config.getString("menus.rule_editor.items.jump.name", "<gold><bold>Salto Base") ?: "<gold><bold>Salto Base"
        val jumpLore = config.getString("menus.rule_editor.items.jump.lore_level", "<gray>Nivel: <yellow>{level}") ?: "<gray>Nivel: <yellow>{level}"
        gui.setItem(13, ItemBuilder.from(Material.RABBIT_FOOT)
            .name(ColorTranslator.translate("<!italic>$jumpName"))
            .lore(ColorTranslator.translate("<!italic>${jumpLore.replace("{level}", jumpVal.toString())}"))
            .asGuiItem {
                settings.jumpMultiplier = if (jumpVal >= 3) null else jumpVal + 1
                abrir(player)
            })

        // Blindness Role
        val blindnessVal = settings.blindnessRole ?: "NONE"
        val blindnessNext = when(blindnessVal) {
            "NONE" -> "KILLER"
            "KILLER" -> "SURVIVOR"
            else -> "NONE"
        }
        val blindName = config.getString("menus.rule_editor.items.blindness.name", "<gold><bold>Ceguera Permanente") ?: "<gold><bold>Ceguera Permanente"
        val blindLore = config.getString("menus.rule_editor.items.blindness.lore_role", "<gray>Aplica a: <yellow>{role}") ?: "<gray>Aplica a: <yellow>{role}"
        gui.setItem(14, ItemBuilder.from(Material.ENDER_EYE)
            .name(ColorTranslator.translate("<!italic>$blindName"))
            .lore(ColorTranslator.translate("<!italic>${blindLore.replace("{role}", blindnessVal)}"))
            .asGuiItem {
                settings.blindnessRole = if (blindnessNext == "NONE") null else blindnessNext
                abrir(player)
            })

        // Killer Health
        val kHealth = settings.killerHealth ?: 160.0
        val khealthName = config.getString("menus.rule_editor.items.khealth.name", "<gold><bold>Vida Asesino") ?: "<gold><bold>Vida Asesino"
        val khealthLore = config.getString("menus.rule_editor.items.khealth.lore_hearts", "<gray>Corazones: <red>{hearts}") ?: "<gray>Corazones: <red>{hearts}"
        gui.setItem(15, ItemBuilder.from(Material.REDSTONE_BLOCK)
            .name(ColorTranslator.translate("<!italic>$khealthName"))
            .lore(ColorTranslator.translate("<!italic>${khealthLore.replace("{hearts}", (kHealth / 2).toString())}"))
            .asGuiItem {
                settings.killerHealth = if (kHealth >= 300.0) 20.0 else kHealth + 20.0
                abrir(player)
            })

        // Survivor Health
        val sHealth = settings.survivorHealth ?: 20.0
        val shealthName = config.getString("menus.rule_editor.items.shealth.name", "<gold><bold>Vida Superviviente") ?: "<gold><bold>Vida Superviviente"
        val shealthLore = config.getString("menus.rule_editor.items.shealth.lore_hearts", "<gray>Corazones: <red>{hearts}") ?: "<gray>Corazones: <red>{hearts}"
        gui.setItem(16, ItemBuilder.from(Material.APPLE)
            .name(ColorTranslator.translate("<!italic>$shealthName"))
            .lore(ColorTranslator.translate("<!italic>${shealthLore.replace("{hearts}", (sHealth / 2).toString())}"))
            .asGuiItem {
                settings.survivorHealth = if (sHealth >= 100.0) 2.0 else sHealth + 2.0
                abrir(player)
            })

        // Back button
        gui.setItem(31, ItemBuilder.from(Material.ARROW)
            .name(ColorTranslator.translate("<!italic>$backName"))
            .asGuiItem {
                PrivateLobbyMenu(plugin, session).abrir(player)
            })

        gui.open(player)
    }
}
