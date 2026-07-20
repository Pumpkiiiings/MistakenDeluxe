import re
import glob

def fix_file(filepath, replacements):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    for pat, repl in replacements:
        content = re.sub(pat, repl, content)
        
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

replacements = [
    (r'fun createDisplay\(\):\s*BlockDisplay\b', r'fun createDisplay(): liric.mistaken.packet.fake.VirtualBlockDisplay'),
    (r'MutableList<Entity>', r'MutableList<liric.mistaken.packet.fake.VirtualDisplay>'),
    (r'MutableList<BlockDisplay>', r'MutableList<liric.mistaken.packet.fake.VirtualBlockDisplay>'),
    (r'MutableList<ItemDisplay>', r'MutableList<liric.mistaken.packet.fake.VirtualItemDisplay>'),
    (r'var textDisplay:\s*TextDisplay\?', r'var textDisplay: liric.mistaken.packet.fake.VirtualTextDisplay?'),
    (r'fun createDisplay\(startLoc: Location\):\s*BlockDisplay\b', r'fun createDisplay(startLoc: Location): liric.mistaken.packet.fake.VirtualBlockDisplay'),
    (r'fun spawnScytheDisplay\(player: Player\):\s*BlockDisplay\b', r'fun spawnScytheDisplay(player: Player): liric.mistaken.packet.fake.VirtualBlockDisplay'),
    (r'var blockDisplay:\s*BlockDisplay\b', r'var blockDisplay: liric.mistaken.packet.fake.VirtualBlockDisplay'),
    (r'var blockDisplay:\s*BlockDisplay\?', r'var blockDisplay: liric.mistaken.packet.fake.VirtualBlockDisplay?'),
    (r'var itemDisplay:\s*Entity\b', r'var itemDisplay: liric.mistaken.packet.fake.VirtualItemDisplay'),
    (r'var itemDisplay:\s*Entity\?', r'var itemDisplay: liric.mistaken.packet.fake.VirtualItemDisplay?'),
    (r'fun crearItemOrbitante\(loc: Location, mat: Material\):\s*ItemDisplay\b', r'fun crearItemOrbitante(loc: Location, mat: Material): liric.mistaken.packet.fake.VirtualItemDisplay'),
    (r'val temporaryEntities = ArrayList<Entity>\(\)', r'val temporaryEntities = ArrayList<liric.mistaken.packet.fake.VirtualDisplay>()'),
    (r'val carta:\s*Entity\b', r'val carta: liric.mistaken.packet.fake.VirtualItemDisplay'),
    (r'fun spawnBaguette\(startLoc: Location, player: Player\):\s*BlockDisplay\b', r'fun spawnBaguette(startLoc: Location, player: Player): liric.mistaken.packet.fake.VirtualBlockDisplay'),
    (r'\):\s*BlockDisplay\?', r'): liric.mistaken.packet.fake.VirtualBlockDisplay?'),
    (r'val t = display.transformation;', r'val t = display.transformation!!;'),
    (r'fun showBlocks\(loc: Location, items: List<Material>, callback: \(MutableList<liric.mistaken.packet.fake.VirtualDisplay>\) -> Unit\)', r'fun showBlocks(loc: Location, items: List<Material>, callback: (MutableList<liric.mistaken.packet.fake.VirtualDisplay>) -> Unit)'),
]

files = glob.glob('MistakenDeluxe-Core/src/main/java/liric/mistaken/**/*.kt', recursive=True)
for f in files:
    fix_file(f, replacements)
