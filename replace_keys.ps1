$replacements = @{
    '"habilidades.pedido-impacto-asesino"' = '"skills.killer-hit-request"'
    '"habilidades.roca-impacto-exito"' = '"skills.rock-hit-success"'
    '"habilidades.pedido-recibido-cura"' = '"skills.heal-request-received"'
    '"shop.estado-seleccionado"' = '"shop.state-selected"'
    '"shop.estado-poseido"' = '"shop.state-owned"'
    '"shop.estado-comprar"' = '"shop.state-buy"'
    '"shop.estado-precio"' = '"shop.state-price"'
    '"shop.abilityes-titulo"' = '"shop.abilities-title"'
    '"shop.clase-humana"' = '"shop.human-class"'
    '"shop.estado-comprar-survivor"' = '"shop.state-buy-survivor"'
    '"shop.comprado"' = '"shop.purchased"'
    '"shop.seleccionado"' = '"shop.selected"'
    '"shop.ya-seleccionado"' = '"shop.already-selected"'
    '"menus.tienda_principal.items.killers.nombre"' = '"menus.main_shop.items.killers.name"'
    '"menus.tienda_principal.items.killers.lore"' = '"menus.main_shop.items.killers.lore"'
    '"menus.tienda_principal.items.survivors.nombre"' = '"menus.main_shop.items.survivors.name"'
    '"menus.tienda_principal.items.survivors.lore"' = '"menus.main_shop.items.survivors.lore"'
    
    'habilidades:' = 'skills:'
    'pedido-impacto-asesino:' = 'killer-hit-request:'
    'roca-impacto-exito:' = 'rock-hit-success:'
    'pedido-recibido-cura:' = 'heal-request-received:'
    'estado-seleccionado:' = 'state-selected:'
    'estado-poseido:' = 'state-owned:'
    'estado-comprar:' = 'state-buy:'
    'estado-precio:' = 'state-price:'
    'abilityes-titulo:' = 'abilities-title:'
    'clase-humana:' = 'human-class:'
    'estado-comprar-survivor:' = 'state-buy-survivor:'
    'comprado:' = 'purchased:'
    'seleccionado:' = 'selected:'
    'ya-seleccionado:' = 'already-selected:'
    'tienda_principal:' = 'main_shop:'
    'nombre:' = 'name:'
}

$files = Get-ChildItem -Path "C:\Users\Pumpkings\Desktop\MistakenDeluxe-main" -Recurse -File -Include *.kt,*.yml

foreach ($file in $files) {
    try {
        $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        $original = $content
        
        foreach ($key in $replacements.Keys) {
            $content = $content.Replace($key, $replacements[$key])
        }
        
        if ($content -cne $original) {
            [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
            Write-Host "Updated $($file.FullName)"
        }
    } catch {
        Write-Host "Skipped $($file.FullName)"
    }
}
