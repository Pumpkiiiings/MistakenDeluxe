import os
import glob
import re

base_dir = r"C:\Users\Pumpkings\Desktop\MistakenDeluxe-main\MistakenDeluxe-Core\src\main\java\liric\mistaken\menu\menus"

replacements = [
    # PrivateLobbyMenu
    (
        r'player\.sendMessage\(ColorTranslator\.translate\("<green><bold>Iniciando partida privada!"\)\)',
        r'player.sendMessage(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.private_lobby.messages.start_game", "<green><bold>Iniciando partida privada!", "messages")))'
    ),
    # MapSelectorMenu
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<green>Mapa selected: \$\{settings\.forcedMap \?: \\"AUTOMÁTICO\\"\}"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.map_selector.messages.map_selected", "<green>Mapa selected: {map}", "messages").replace("{map}", settings.forcedMap ?: "AUTOMÁTICO")))'
    ),
    # ModeSelectorMenu
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<green>Modo selected: \$\{settings\.forcedMode\?\.name \?: \\"AUTOMÁTICO\\"\}"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.mode_selector.messages.mode_selected", "<green>Modo selected: {mode}", "messages").replace("{mode}", settings.forcedMode?.name ?: "AUTOMÁTICO")))'
    ),
    # CharacterSelectorMenu
    (
        r'player\.sendMessage\(ColorTranslator\.translate\("<red>No puedes bloquear a Slasher, es la clase por defecto\."\)\)',
        r'player.sendMessage(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.character_selector.messages.slasher_locked", "<red>No puedes bloquear a Slasher, es la clase por defecto.", "messages")))'
    ),
    (
        r'player\.sendMessage\(ColorTranslator\.translate\("<red>No puedes bloquear a Civilian, es la clase por defecto\."\)\)',
        r'player.sendMessage(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.character_selector.messages.civilian_locked", "<red>No puedes bloquear a Civilian, es la clase por defecto.", "messages")))'
    ),
    # PlayerSelectorMenu
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<green>Has modificado el rol de: <yellow>\$name"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.player_selector.messages.role_changed", "<green>Has modificado el rol de: <yellow>{player}", "messages").replace("{player}", name)))'
    ),
    # RuleEditorMenu
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<green>Regla modificada: Glowing \$\{if \(settings\.glowingEnabled\) \\"ON\\" else \\"OFF\\"\}\"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.glowing_changed", "<green>Regla modificada: Glowing {state}", "messages").replace("{state}", if (settings.glowingEnabled) "ON" else "OFF")))'
    ),
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<green>Regla modificada: Latidos \$\{if \(settings\.heartbeatsEnabled \!= false\) \\"ON\\" else \\"OFF\\"\}\"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.heartbeats_changed", "<green>Regla modificada: Latidos {state}", "messages").replace("{state}", if (settings.heartbeatsEnabled != false) "ON" else "OFF")))'
    ),
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<yellow>Velocidad base ajustada a: \$\{settings\.speedMultiplier \?: 0\}"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.speed_changed", "<yellow>Velocidad base ajustada a: {value}", "messages").replace("{value}", (settings.speedMultiplier ?: 0).toString())))'
    ),
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<yellow>Salto base ajustado a: \$\{settings\.jumpMultiplier \?: 0\}"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.jump_changed", "<yellow>Salto base ajustado a: {value}", "messages").replace("{value}", (settings.jumpMultiplier ?: 0).toString())))'
    ),
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<aqua>Ceguera asignada a: \$\{settings\.blindnessRole \?: \\"NONE\\"\}\"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.blindness_changed", "<aqua>Ceguera asignada a: {value}", "messages").replace("{value}", settings.blindnessRole?.name ?: "NONE")))'
    ),
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<red>Vida de Asesino ajustada a: \$\{settings\.killerHealth\!\! / 2\} corazones"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.khealth_changed", "<red>Vida de Asesino ajustada a: {value} corazones", "messages").replace("{value}", (settings.killerHealth!! / 2).toString())))'
    ),
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<red>Vida de Superviviente ajustada a: \$\{settings\.survivorHealth\!\! / 2\} corazones"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.shealth_changed", "<red>Vida de Superviviente ajustada a: {value} corazones", "messages").replace("{value}", (settings.survivorHealth!! / 2).toString())))'
    ),
    (
        r'player\.sendActionBar\(ColorTranslator\.translate\("<yellow>Duración ajustada a: \$newFormatted"\)\)',
        r'player.sendActionBar(ColorTranslator.translate(liric.mistaken.config.engine.core.MessageService.getRawString(player, "menus.rule_editor.messages.duration_changed", "<yellow>Duración ajustada a: {value}", "messages").replace("{value}", newFormatted)))'
    )
]

for filename in glob.glob(os.path.join(base_dir, "*.kt")):
    with open(filename, "r", encoding="utf-8") as f:
        content = f.read()
    
    modified = False
    for search, replace in replacements:
        new_content = re.sub(search, replace, content)
        if new_content != content:
            content = new_content
            modified = True
            
    if modified:
        with open(filename, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Modificado {filename}")

print("Terminado.")
