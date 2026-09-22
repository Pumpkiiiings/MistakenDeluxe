package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.PrivateGameSettings
import liric.mistaken.config.Messages
import org.bukkit.Material
import org.bukkit.entity.Player
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.utils.color.ColorTranslator

class RuleEditorMenu(private val plugin: Mistaken, private val session: GameSession) {

    fun abrir(player: Player) {
        val config = liric.mistaken.config.engine.core.ConfigManager.getMenuConfig("private_lobby")
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
        val charactersSlot = config.getInt("menus.rule_editor.slots.characters", 27)
        val backSlot = config.getInt("menus.rule_editor.slots.back", 40)

        val gui = Gui.gui()
            .title(ColorTranslator.translate("<!italic>$titleText"))
            .rows(rows)
            .disableAllInteractions()
            .create()

        val section = config.getConfigurationSection("menus.rule_editor")
        if (section != null) {
            liric.mistaken.utils.MenuUtils.applyOverlay(gui, section, player)
        }

        val settings = session.settings ?: PrivateGameSettings().also { session.settings = it }

        
        val glowingMat = if (settings.glowingEnabled) Material.GLOWSTONE_DUST else Material.GUNPOWDER
        val glowingColor = if (settings.glowingEnabled) "<green>" else "<red>"
        val glowingName = config.getString("menus.rule_editor.items.glowing.name", "<gold><bold>Glowing Constante") ?: "<gold><bold>Glowing Constante"
        val glowingLore = config.getString("menus.rule_editor.items.glowing.lore_state", "<gray>Estado: {color}{state}") ?: "<gray>Estado: {color}{state}"
        val glowingStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.glowing", glowingMat)
        glowingStack.editMeta {
            it.displayName(ColorTranslator.translate("<!italic>$glowingName"))
            it.lore(listOf(ColorTranslator.translate("<!italic>${glowingLore.replace("{color}", glowingColor).replace("{state}", settings.glowingEnabled.toString())}")))
        }
        gui.setItem(glowingSlot, dev.triumphteam.gui.guis.GuiItem(glowingStack) {
            settings.glowingEnabled = !settings.glowingEnabled
            player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
            player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.glowing_changed", "<green>Regla modificada: Glowing {state}", "messages").replace("{state}", if (settings.glowingEnabled) "ON" else "OFF")))
            abrir(player)
        })

        
        val hbMat = if (settings.heartbeatsEnabled != false) Material.NOTE_BLOCK else Material.JUKEBOX
        val hbColor = if (settings.heartbeatsEnabled != false) "<green>" else "<red>"
        val hbName = config.getString("menus.rule_editor.items.heartbeats.name", "<gold><bold>Latidos (Heartbeats)") ?: "<gold><bold>Latidos (Heartbeats)"
        val hbLore = config.getString("menus.rule_editor.items.heartbeats.lore_state", "<gray>Estado: {color}{state}") ?: "<gray>Estado: {color}{state}"
        val heartbeatsSlotStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.heartbeats", hbMat); heartbeatsSlotStack.editMeta { it.displayName(ColorTranslator.translate("<!italic>$hbName")); it.lore(listOf(ColorTranslator.translate("<!italic>${hbLore.replace("{color}", hbColor).replace("{state}", (settings.heartbeatsEnabled != false).toString())}"))) }; gui.setItem(heartbeatsSlot, dev.triumphteam.gui.guis.GuiItem(heartbeatsSlotStack) {
                settings.heartbeatsEnabled = (settings.heartbeatsEnabled == false)
                player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
                player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.heartbeats_changed", "<green>Regla modificada: Latidos {state}", "messages").replace("{state}", if (settings.heartbeatsEnabled != false) "ON" else "OFF")))
                abrir(player)
            })

        
        val speedVal = settings.speedMultiplier ?: 0
        val speedName = config.getString("menus.rule_editor.items.speed.name", "<gold><bold>Velocidad Base") ?: "<gold><bold>Velocidad Base"
        val speedLore = config.getString("menus.rule_editor.items.speed.lore_level", "<gray>Nivel: <yellow>{level}") ?: "<gray>Nivel: <yellow>{level}"
        val speedSlotStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.speed", Material.SUGAR); speedSlotStack.editMeta { it.displayName(ColorTranslator.translate("<!italic>$speedName")); it.lore(listOf(ColorTranslator.translate("<!italic>${speedLore.replace("{level}", speedVal.toString())}"))) }; gui.setItem(speedSlot, dev.triumphteam.gui.guis.GuiItem(speedSlotStack) {
                settings.speedMultiplier = if (speedVal >= 3) null else speedVal + 1
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
                player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.speed_changed", "<yellow>Velocidad base ajustada a: {value}", "messages").replace("{value}", (settings.speedMultiplier ?: 0).toString())))
                abrir(player)
            })

        
        val jumpVal = settings.jumpMultiplier ?: 0
        val jumpName = config.getString("menus.rule_editor.items.jump.name", "<gold><bold>Salto Base") ?: "<gold><bold>Salto Base"
        val jumpLore = config.getString("menus.rule_editor.items.jump.lore_level", "<gray>Nivel: <yellow>{level}") ?: "<gray>Nivel: <yellow>{level}"
        val jumpSlotStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.jump", Material.RABBIT_FOOT); jumpSlotStack.editMeta { it.displayName(ColorTranslator.translate("<!italic>$jumpName")); it.lore(listOf(ColorTranslator.translate("<!italic>${jumpLore.replace("{level}", jumpVal.toString())}"))) }; gui.setItem(jumpSlot, dev.triumphteam.gui.guis.GuiItem(jumpSlotStack) {
                settings.jumpMultiplier = if (jumpVal >= 3) null else jumpVal + 1
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
                player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.jump_changed", "<yellow>Salto base ajustado a: {value}", "messages").replace("{value}", (settings.jumpMultiplier ?: 0).toString())))
                abrir(player)
            })

        
        val blindnessVal = settings.blindnessRole ?: "NONE"
        val blindnessNext = when(blindnessVal) {
            "NONE" -> "KILLER"
            "KILLER" -> "SURVIVOR"
            else -> "NONE"
        }
        val blindName = config.getString("menus.rule_editor.items.blindness.name", "<gold><bold>Ceguera Permanente") ?: "<gold><bold>Ceguera Permanente"
        val blindLore = config.getString("menus.rule_editor.items.blindness.lore_role", "<gray>Aplica a: <yellow>{role}") ?: "<gray>Aplica a: <yellow>{role}"
        val blindnessSlotStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.blindness", Material.ENDER_EYE); blindnessSlotStack.editMeta { it.displayName(ColorTranslator.translate("<!italic>$blindName")); it.lore(listOf(ColorTranslator.translate("<!italic>${blindLore.replace("{role}", blindnessVal)}"))) }; gui.setItem(blindnessSlot, dev.triumphteam.gui.guis.GuiItem(blindnessSlotStack) {
                settings.blindnessRole = if (blindnessNext == "NONE") null else blindnessNext
                player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
                player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.blindness_changed", "<aqua>Ceguera asignada a: {value}", "messages").replace("{value}", settings.blindnessRole ?: "NONE")))
                abrir(player)
            })

        
        val kHealth = settings.killerHealth ?: 160.0
        val khealthName = config.getString("menus.rule_editor.items.khealth.name", "<gold><bold>Vida Asesino") ?: "<gold><bold>Vida Asesino"
        val khealthLore = config.getString("menus.rule_editor.items.khealth.lore_hearts", "<gray>Corazones: <red>{hearts}") ?: "<gray>Corazones: <red>{hearts}"
        val khealthSlotStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.khealth", Material.REDSTONE_BLOCK); khealthSlotStack.editMeta { it.displayName(ColorTranslator.translate("<!italic>$khealthName")); it.lore(listOf(ColorTranslator.translate("<!italic>${khealthLore.replace("{hearts}", (kHealth / 2).toString())}"))) }; gui.setItem(khealthSlot, dev.triumphteam.gui.guis.GuiItem(khealthSlotStack) {
                settings.killerHealth = if (kHealth >= 300.0) 20.0 else kHealth + 20.0
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.khealth_changed", "<red>Vida de Asesino ajustada a: {value} corazones", "messages").replace("{value}", (settings.killerHealth!! / 2).toString())))
                abrir(player)
            })

        
        val sHealth = settings.survivorHealth ?: 20.0
        val shealthName = config.getString("menus.rule_editor.items.shealth.name", "<gold><bold>Vida Superviviente") ?: "<gold><bold>Vida Superviviente"
        val shealthLore = config.getString("menus.rule_editor.items.shealth.lore_hearts", "<gray>Corazones: <red>{hearts}") ?: "<gray>Corazones: <red>{hearts}"
        val shealthSlotStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.shealth", Material.APPLE); shealthSlotStack.editMeta { it.displayName(ColorTranslator.translate("<!italic>$shealthName")); it.lore(listOf(ColorTranslator.translate("<!italic>${shealthLore.replace("{hearts}", (sHealth / 2).toString())}"))) }; gui.setItem(shealthSlot, dev.triumphteam.gui.guis.GuiItem(shealthSlotStack) {
                settings.survivorHealth = if (sHealth >= 100.0) 2.0 else sHealth + 2.0
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.shealth_changed", "<red>Vida de Superviviente ajustada a: {value} corazones", "messages").replace("{value}", (settings.survivorHealth!! / 2).toString())))
                abrir(player)
            })

        
        val currentDuration = settings.gameDuration ?: 300 
        val durationName = config.getString("menus.rule_editor.items.duration.name", "<gold><bold>Duración de Partida") ?: "<gold><bold>Duración de Partida"
        val durationLore = config.getString("menus.rule_editor.items.duration.lore_time", "<gray>Tiempo: <yellow>{time}") ?: "<gray>Tiempo: <yellow>{time}"
        val minutes = currentDuration / 60
        val seconds = currentDuration % 60
        val formattedTime = String.format("%02d Minutos, %02d Segundos", minutes, seconds)

        val durationSlotStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.duration", Material.CLOCK); durationSlotStack.editMeta { it.displayName(ColorTranslator.translate("<!italic>$durationName")); it.lore(listOf(ColorTranslator.translate("<!italic>${durationLore.replace("{time}", formattedTime)}"))) }; gui.setItem(durationSlot, dev.triumphteam.gui.guis.GuiItem(durationSlotStack) { event ->
                
                var newDuration = currentDuration
                if (event.isLeftClick) {
                    newDuration += 60
                    if (newDuration > 1800) newDuration = 60 
                } else if (event.isRightClick) {
                    newDuration -= 60
                    if (newDuration < 60) newDuration = 1800 
                }
                
                settings.gameDuration = newDuration
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                val newMins = newDuration / 60
                val newSecs = newDuration % 60
                val newFormatted = String.format("%02d Minutos, %02d Segundos", newMins, newSecs)
                player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.duration_changed", "<yellow>Duración ajustada a: {value}", "messages").replace("{value}", newFormatted)))
                abrir(player)
            })

        
        val charactersName = config.getString("menus.rule_editor.items.characters.name", "<gold><bold>Clases Permitidas") ?: "<gold><bold>Clases Permitidas"
        val charactersLoreList = config.getStringList("menus.rule_editor.items.characters.lore")
        val charactersLore = if (charactersLoreList.isNotEmpty()) {
            charactersLoreList.map { ColorTranslator.translate("<!italic>$it") }
        } else {
            listOf(ColorTranslator.translate("<!italic><gray>Selecciona qu asesinos y"), ColorTranslator.translate("<!italic><gray>supervivientes se pueden usar."))
        }
        val charactersSlotStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.characters", Material.ZOMBIE_HEAD); charactersSlotStack.editMeta { it.displayName(ColorTranslator.translate("<!italic>$charactersName")); it.lore(charactersLore) }; gui.setItem(charactersSlot, dev.triumphteam.gui.guis.GuiItem(charactersSlotStack) {
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                CharacterSelectorMenu(plugin, session).abrir(player)
            })

        val backNameFallback = config.getString("menus.private_lobby.items.back.name", "<red>Volver") ?: "<red>Volver"
        val backNameFinal = config.getString("menus.rule_editor.items.back.name", backNameFallback) ?: backNameFallback
        
        val backStack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "menus.rule_editor.items.back", Material.ARROW); backStack.editMeta { it.displayName(ColorTranslator.translate("<!italic>$backNameFinal")) }; val backItem = dev.triumphteam.gui.guis.GuiItem(backStack) { event: org.bukkit.event.inventory.InventoryClickEvent ->
                player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 0.8f)
                PrivateLobbyMenu(plugin, session).abrir(player)
            }
        gui.setItem(backSlot, backItem)

        gui.open(player)
    }
}
