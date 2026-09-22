import re

with open('MistakenDeluxe-Core/src/main/java/liric/mistaken/menu/menus/RuleEditorMenu.kt', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'gui\.setItem\(([^,]+),\s*liric\.mistaken\.utils\.MenuUtils\.createConfigItem\(config,\s*"([^"]+)",\s*([^\)]+)\)\s*\.name\((.*?)\)\s*\.lore\((.*?)\)\s*\.asGuiItem\s*\{\s*(event\s*->)?'
def repl(m):
    slot = m.group(1)
    path = m.group(2)
    mat = m.group(3)
    name = m.group(4)
    lore = m.group(5)
    ev = m.group(6) if m.group(6) else ''
    
    return f'''val {slot}Stack = liric.mistaken.utils.MenuUtils.createConfigItem(config, "{path}", {mat})
        {slot}Stack.editMeta {{ it.displayName({name}); it.lore(listOf({lore})) }}
        gui.setItem({slot}, dev.triumphteam.gui.guis.GuiItem({slot}Stack) {{ {ev}'''

content = re.sub(pattern, repl, content)

with open('MistakenDeluxe-Core/src/main/java/liric/mistaken/menu/menus/RuleEditorMenu.kt', 'w', encoding='utf-8') as f:
    f.write(content)
