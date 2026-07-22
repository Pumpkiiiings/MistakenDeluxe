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
        val titleText = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_RULES_TITLE, "<dark_gray>Editor de Reglas", "messages")
        
        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic>$titleText"))
            .rows(4)
            .disableAllInteractions()
            .create()

        val settings = session.settings ?: PrivateGameSettings().also { session.settings = it }

        // Glowing
        val glowingMat = if (settings.glowingEnabled) Material.GLOWSTONE_DUST else Material.GUNPOWDER
        val glowingColor = if (settings.glowingEnabled) "<green>" else "<red>"
        val glowingName = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_RULES_GLOWING, "<gold><bold>Glowing Constante", "messages")
        gui.setItem(10, ItemBuilder.from(glowingMat)
            .name(ColorTranslator.translate("<!italic>$glowingName"))
            .lore(ColorTranslator.translate("<!italic><gray>Estado: $glowingColor${settings.glowingEnabled}"))
            .asGuiItem {
                settings.glowingEnabled = !settings.glowingEnabled
                abrir(player) // Refresh
            })

        // Heartbeats
        val hbMat = if (settings.heartbeatsEnabled != false) Material.NOTE_BLOCK else Material.JUKEBOX
        val hbColor = if (settings.heartbeatsEnabled != false) "<green>" else "<red>"
        val hbName = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_RULES_HEARTBEATS, "<gold><bold>Latidos (Heartbeats)", "messages")
        gui.setItem(11, ItemBuilder.from(hbMat)
            .name(ColorTranslator.translate("<!italic>$hbName"))
            .lore(ColorTranslator.translate("<!italic><gray>Estado: $hbColor${settings.heartbeatsEnabled != false}"))
            .asGuiItem {
                settings.heartbeatsEnabled = (settings.heartbeatsEnabled == false)
                abrir(player)
            })

        // Speed
        val speedVal = settings.speedMultiplier ?: 0
        val speedName = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_RULES_SPEED, "<gold><bold>Velocidad Base", "messages")
        gui.setItem(12, ItemBuilder.from(Material.SUGAR)
            .name(ColorTranslator.translate("<!italic>$speedName"))
            .lore(ColorTranslator.translate("<!italic><gray>Nivel: <yellow>$speedVal"))
            .asGuiItem {
                settings.speedMultiplier = if (speedVal >= 3) null else speedVal + 1
                abrir(player)
            })

        // Jump
        val jumpVal = settings.jumpMultiplier ?: 0
        val jumpName = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_RULES_JUMP, "<gold><bold>Salto Base", "messages")
        gui.setItem(13, ItemBuilder.from(Material.RABBIT_FOOT)
            .name(ColorTranslator.translate("<!italic>$jumpName"))
            .lore(ColorTranslator.translate("<!italic><gray>Nivel: <yellow>$jumpVal"))
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
        val blindName = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_RULES_BLINDNESS, "<gold><bold>Ceguera Permanente", "messages")
        gui.setItem(14, ItemBuilder.from(Material.ENDER_EYE)
            .name(ColorTranslator.translate("<!italic>$blindName"))
            .lore(ColorTranslator.translate("<!italic><gray>Aplica a: <yellow>$blindnessVal"))
            .asGuiItem {
                settings.blindnessRole = if (blindnessNext == "NONE") null else blindnessNext
                abrir(player)
            })

        // Killer Health
        val kHealth = settings.killerHealth ?: 160.0
        val khealthName = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_RULES_KHEALTH, "<gold><bold>Vida Asesino", "messages")
        gui.setItem(15, ItemBuilder.from(Material.REDSTONE_BLOCK)
            .name(ColorTranslator.translate("<!italic>$khealthName"))
            .lore(ColorTranslator.translate("<!italic><gray>Corazones: <red>${kHealth / 2}"))
            .asGuiItem {
                settings.killerHealth = if (kHealth >= 300.0) 20.0 else kHealth + 20.0
                abrir(player)
            })

        // Survivor Health
        val sHealth = settings.survivorHealth ?: 20.0
        val shealthName = PumpkingServiceManager.messages.getRawString(player, Messages.MENUS_PRIVATE_RULES_SHEALTH, "<gold><bold>Vida Superviviente", "messages")
        gui.setItem(16, ItemBuilder.from(Material.APPLE)
            .name(ColorTranslator.translate("<!italic>$shealthName"))
            .lore(ColorTranslator.translate("<!italic><gray>Corazones: <red>${sHealth / 2}"))
            .asGuiItem {
                settings.survivorHealth = if (sHealth >= 100.0) 2.0 else sHealth + 2.0
                abrir(player)
            })

        // Back button
        gui.setItem(31, ItemBuilder.from(Material.ARROW)
            .name(ColorTranslator.translate("<!italic><red>Volver"))
            .asGuiItem {
                gui.close(player)
                PrivateLobbyMenu(plugin, session).abrir(player)
            })

        gui.open(player)
    }
}
