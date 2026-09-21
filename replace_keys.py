import os
import glob

replacements = {
    '"habilidades.pedido-impacto-asesino"': '"skills.killer-hit-request"',
    '"habilidades.roca-impacto-exito"': '"skills.rock-hit-success"',
    '"habilidades.pedido-recibido-cura"': '"skills.heal-request-received"',
    '"shop.estado-seleccionado"': '"shop.state-selected"',
    '"shop.estado-poseido"': '"shop.state-owned"',
    '"shop.estado-comprar"': '"shop.state-buy"',
    '"shop.estado-precio"': '"shop.state-price"',
    '"shop.abilityes-titulo"': '"shop.abilities-title"',
    '"shop.clase-humana"': '"shop.human-class"',
    '"shop.estado-comprar-survivor"': '"shop.state-buy-survivor"',
    '"shop.comprado"': '"shop.purchased"',
    '"shop.seleccionado"': '"shop.selected"',
    '"shop.ya-seleccionado"': '"shop.already-selected"',
    '"menus.tienda_principal.items.killers.nombre"': '"menus.main_shop.items.killers.name"',
    '"menus.tienda_principal.items.killers.lore"': '"menus.main_shop.items.killers.lore"',
    '"menus.tienda_principal.items.survivors.nombre"': '"menus.main_shop.items.survivors.name"',
    '"menus.tienda_principal.items.survivors.lore"': '"menus.main_shop.items.survivors.lore"',
    
    # Also without quotes for YAML files
    'habilidades:': 'skills:',
    'pedido-impacto-asesino:': 'killer-hit-request:',
    'roca-impacto-exito:': 'rock-hit-success:',
    'pedido-recibido-cura:': 'heal-request-received:',
    'estado-seleccionado:': 'state-selected:',
    'estado-poseido:': 'state-owned:',
    'estado-comprar:': 'state-buy:',
    'estado-precio:': 'state-price:',
    'abilityes-titulo:': 'abilities-title:',
    'clase-humana:': 'human-class:',
    'estado-comprar-survivor:': 'state-buy-survivor:',
    'comprado:': 'purchased:',
    'seleccionado:': 'selected:',
    'ya-seleccionado:': 'already-selected:',
    'tienda_principal:': 'main_shop:',
    'nombre:': 'name:'
}

def replace_in_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except UnicodeDecodeError:
        return
    
    original = content
    for k, v in replacements.items():
        if k == 'nombre:' and filepath.endswith('.yml'):
            content = content.replace('nombre:', 'name:')
        else:
            content = content.replace(k, v)
            
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")

for root, _, files in os.walk('C:/Users/Pumpkings/Desktop/MistakenDeluxe-main'):
    for file in files:
        if file.endswith('.kt') or file.endswith('.yml'):
            replace_in_file(os.path.join(root, file))
