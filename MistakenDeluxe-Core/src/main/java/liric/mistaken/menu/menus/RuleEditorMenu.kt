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
        
        val rows = config.getInt("menus.rule_editor.rows", 5)
        val fillerMatStr = config.getString("menus.rule_editor.filler_material", "BLACK_STAINED_GLASS_PANE") ?: "BLACK_STAINED_GLASS_PANE"
        val fillerMat = runCatching { Material.valueOf(fillerMatStr.uppercase()) }.getOrDefault(Material.BLACK_STAINED_GLASS_PANE)

        val glowingSlot = config.getInt("menus.rule_editor.slots.glowing", 19)
        val heartbeatsSlot = config.getInt("menus.rule_editor.slots.heartbeats", 20)
        val speedSlot = config.getInt("menus.rule_editor.slots.speed", 21)
        val jumpSlot = config.getInt("menus.rule_editor.slots.jump", 22)
        val blindnessSlot = config.getInt("menus.rule_editor.slots.blindness", 23)
        val khealthSlot = config.getInt("menus.rule_editor.slots.khealth", 24)
        val shealthSlot = config.getInt("menus.rule_editor.slots.shealth", 25)
        val durationSlot = config.getInt("menus.rule_editor.slots.duration", 26)
        val backSlot = config.getInt("menus.rule_editor.slots.back", 40)

        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic>$titleText"))
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

        // Glowing
        val glowingMat = if (settings.glowingEnabled) Material.GLOWSTONE_DUST else Material.GUNPOWDER
        val glowingColor = if (settings.glowingEnabled) "<green>" else "<red>"
        val glowingName = config.getString("menus.rule_editor.items.glowing.name", "<gold><bold>Glowing Constante") ?: "<gold><bold>Glowing Constante"
        val glowingLore = config.getString("menus.rule_editor.items.glowing.lore_state", "<gray>Estado: {color}{state}") ?: "<gray>Estado: {color}{state}"
        gui.setItem(glowingSlot, ItemBuilder.from(glowingMat)
            .name(ColorTranslator.translate("<!italic>$glowingName"))
            .lore(ColorTranslator.translate("<!italic>${glowingLore.replace("{color}", glowingColor).replace("{state}", settings.glowingEnabled.toString())}"))
            .asGuiItem {
                settings.glowingEnabled = !settings.glowingEnabled
                player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
                player.sendActionBar(ColorTranslator.translate("<green>Regla modificada: Glowing ${if (settings.glowingEnabled) "ON" else "OFF"}"))
                abrir(player) // Refresh
            })

        // Heartbeats
        val hbMat = if (settings.heartbeatsEnabled != false) Material.NOTE_BLOCK else Material.JUKEBOX
        val hbColor = if (settings.heartbeatsEnabled != false) "<green>" else "<red>"
        val hbName = config.getString("menus.rule_editor.items.heartbeats.name", "<gold><bold>Latidos (Heartbeats)") ?: "<gold><bold>Latidos (Heartbeats)"
        val hbLore = config.getString("menus.rule_editor.items.heartbeats.lore_state", "<gray>Estado: {color}{state}") ?: "<gray>Estado: {color}{state}"
        gui.setItem(heartbeatsSlot, ItemBuilder.from(hbMat)
            .name(ColorTranslator.translate("<!italic>$hbName"))
            .lore(ColorTranslator.translate("<!italic>${hbLore.replace("{color}", hbColor).replace("{state}", (settings.heartbeatsEnabled != false).toString())}"))
            .asGuiItem {
                settings.heartbeatsEnabled = (settings.heartbeatsEnabled == false)
                player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
                player.sendActionBar(ColorTranslator.translate("<green>Regla modificada: Latidos ${if (settings.heartbeatsEnabled != false) "ON" else "OFF"}"))
                abrir(player)
            })

        // Speed
        val speedVal = settings.speedMultiplier ?: 0
        val speedName = config.getString("menus.rule_editor.items.speed.name", "<gold><bold>Velocidad Base") ?: "<gold><bold>Velocidad Base"
        val speedLore = config.getString("menus.rule_editor.items.speed.lore_level", "<gray>Nivel: <yellow>{level}") ?: "<gray>Nivel: <yellow>{level}"
        gui.setItem(speedSlot, ItemBuilder.from(Material.SUGAR)
            .name(ColorTranslator.translate("<!italic>$speedName"))
            .lore(ColorTranslator.translate("<!italic>${speedLore.replace("{level}", speedVal.toString())}"))
            .asGuiItem {
                settings.speedMultiplier = if (speedVal >= 3) null else speedVal + 1
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
                player.sendActionBar(ColorTranslator.translate("<yellow>Velocidad base ajustada a: ${settings.speedMultiplier ?: 0}"))
                abrir(player)
            })

        // Jump
        val jumpVal = settings.jumpMultiplier ?: 0
        val jumpName = config.getString("menus.rule_editor.items.jump.name", "<gold><bold>Salto Base") ?: "<gold><bold>Salto Base"
        val jumpLore = config.getString("menus.rule_editor.items.jump.lore_level", "<gray>Nivel: <yellow>{level}") ?: "<gray>Nivel: <yellow>{level}"
        gui.setItem(jumpSlot, ItemBuilder.from(Material.RABBIT_FOOT)
            .name(ColorTranslator.translate("<!italic>$jumpName"))
            .lore(ColorTranslator.translate("<!italic>${jumpLore.replace("{level}", jumpVal.toString())}"))
            .asGuiItem {
                settings.jumpMultiplier = if (jumpVal >= 3) null else jumpVal + 1
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
                player.sendActionBar(ColorTranslator.translate("<yellow>Salto base ajustado a: ${settings.jumpMultiplier ?: 0}"))
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
        gui.setItem(blindnessSlot, ItemBuilder.from(Material.ENDER_EYE)
            .name(ColorTranslator.translate("<!italic>$blindName"))
            .lore(ColorTranslator.translate("<!italic>${blindLore.replace("{role}", blindnessVal)}"))
            .asGuiItem {
                settings.blindnessRole = if (blindnessNext == "NONE") null else blindnessNext
                player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
                player.sendActionBar(ColorTranslator.translate("<aqua>Ceguera asignada a: ${settings.blindnessRole ?: "NONE"}"))
                abrir(player)
            })

        // Killer Health
        val kHealth = settings.killerHealth ?: 160.0
        val khealthName = config.getString("menus.rule_editor.items.khealth.name", "<gold><bold>Vida Asesino") ?: "<gold><bold>Vida Asesino"
        val khealthLore = config.getString("menus.rule_editor.items.khealth.lore_hearts", "<gray>Corazones: <red>{hearts}") ?: "<gray>Corazones: <red>{hearts}"
        gui.setItem(khealthSlot, ItemBuilder.from(Material.REDSTONE_BLOCK)
            .name(ColorTranslator.translate("<!italic>$khealthName"))
            .lore(ColorTranslator.translate("<!italic>${khealthLore.replace("{hearts}", (kHealth / 2).toString())}"))
            .asGuiItem {
                settings.killerHealth = if (kHealth >= 300.0) 20.0 else kHealth + 20.0
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                player.sendActionBar(ColorTranslator.translate("<red>Vida de Asesino ajustada a: ${settings.killerHealth!! / 2} corazones"))
                abrir(player)
            })

        // Survivor Health
        val sHealth = settings.survivorHealth ?: 20.0
        val shealthName = config.getString("menus.rule_editor.items.shealth.name", "<gold><bold>Vida Superviviente") ?: "<gold><bold>Vida Superviviente"
        val shealthLore = config.getString("menus.rule_editor.items.shealth.lore_hearts", "<gray>Corazones: <red>{hearts}") ?: "<gray>Corazones: <red>{hearts}"
        gui.setItem(shealthSlot, ItemBuilder.from(Material.APPLE)
            .name(ColorTranslator.translate("<!italic>$shealthName"))
            .lore(ColorTranslator.translate("<!italic>${shealthLore.replace("{hearts}", (sHealth / 2).toString())}"))
            .asGuiItem {
                settings.survivorHealth = if (sHealth >= 100.0) 2.0 else sHealth + 2.0
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                player.sendActionBar(ColorTranslator.translate("<red>Vida de Superviviente ajustada a: ${settings.survivorHealth!! / 2} corazones"))
                abrir(player)
            })

        // Game Duration
        val currentDuration = settings.gameDuration ?: 300 // default 5 minutes in seconds
        val durationName = config.getString("menus.rule_editor.items.duration.name", "<gold><bold>Duración de Partida") ?: "<gold><bold>Duración de Partida"
        val durationLore = config.getString("menus.rule_editor.items.duration.lore_time", "<gray>Tiempo: <yellow>{time}") ?: "<gray>Tiempo: <yellow>{time}"
        val minutes = currentDuration / 60
        val seconds = currentDuration % 60
        val formattedTime = String.format("%02d Minutos, %02d Segundos", minutes, seconds)

        gui.setItem(durationSlot, ItemBuilder.from(Material.CLOCK)
            .name(ColorTranslator.translate("<!italic>$durationName"))
            .lore(ColorTranslator.translate("<!italic>${durationLore.replace("{time}", formattedTime)}"))
            .asGuiItem { event ->
                // Click Izquierdo: +1 minuto, Click Derecho: -1 minuto
                var newDuration = currentDuration
                if (event.isLeftClick) {
                    newDuration += 60
                    if (newDuration > 1800) newDuration = 60 // Max 30 minutes, wraps to 1 min
                } else if (event.isRightClick) {
                    newDuration -= 60
                    if (newDuration < 60) newDuration = 1800 // Min 1 min, wraps to 30 min
                }
                
                settings.gameDuration = newDuration
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                val newMins = newDuration / 60
                val newSecs = newDuration % 60
                val newFormatted = String.format("%02d Minutos, %02d Segundos", newMins, newSecs)
                player.sendActionBar(ColorTranslator.translate("<yellow>Duración ajustada a: $newFormatted"))
                abrir(player)
            })

        // Back button
        val backItem = ItemBuilder.from(Material.ARROW)
            .name(ColorTranslator.translate("<!italic>$backName"))
            .asGuiItem {
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 0.8f)
                PrivateLobbyMenu(plugin, session).abrir(player)
            }
        gui.setItem(backSlot, backItem)

        gui.open(player)
    }
}
