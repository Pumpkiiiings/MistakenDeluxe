$basePath = "C:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken"

# Fix Entity303
$entity303 = "$basePath\roles\killers\clases\Entity303.kt"
$content = Get-Content $entity303 -Raw
$content = $content -replace 'crearBloqueOrbitante\(loc: Location', 'crearBloqueOrbitante(player: Player, loc: Location'
$content = $content -replace 'crearBloqueOrbitante\(player\.location', 'crearBloqueOrbitante(player, player.location'
Set-Content $entity303 $content -Encoding UTF8

# Fix Herobrine
$herobrine = "$basePath\roles\killers\clases\Herobrine.kt"
$content = Get-Content $herobrine -Raw
$content = $content -replace 'org.bukkit.plugin.java.JavaPlugin.getPlugin\(liric.mistaken.Mistaken::class.java\).sessionManager.getSession\(player\)\?.getPlayers\(\) \?: listOf\(player\)', 'org.bukkit.Bukkit.getOnlinePlayers().toList()'
Set-Content $herobrine $content -Encoding UTF8

# Fix NullAsesino
$nullAsesino = "$basePath\roles\killers\clases\NullAsesino.kt"
$content = Get-Content $nullAsesino -Raw
$content = $content -replace 'org.bukkit.plugin.java.JavaPlugin.getPlugin\(liric.mistaken.Mistaken::class.java\).sessionManager.getSession\(player\)\?.getPlayers\(\) \?: listOf\(player\)', 'org.bukkit.Bukkit.getOnlinePlayers().toList()'
Set-Content $nullAsesino $content -Encoding UTF8

# Fix Romeo
$romeo = "$basePath\roles\killers\clases\Romeo.kt"
$content = Get-Content $romeo -Raw
$content = $content -replace 'org.bukkit.plugin.java.JavaPlugin.getPlugin\(liric.mistaken.Mistaken::class.java\).sessionManager.getSession\(player\)\?.getPlayers\(\) \?: listOf\(player\)', 'org.bukkit.Bukkit.getOnlinePlayers().toList()'
Set-Content $romeo $content -Encoding UTF8

# Fix langconfig
$survivors = @('Civilian.kt', 'DeliveryMan.kt', 'Minty.kt', 'Notch.kt')
foreach ($surv in $survivors) {
    $file = "$basePath\roles\survivors\clases\$surv"
    $content = Get-Content $file -Raw
    $content = $content -replace 'langconfig.getString', 'langConfig.getString'
    Set-Content $file $content -Encoding UTF8
}

# Fix HitboxVisualizer
$hitbox = "$basePath\utils\misc\HitboxVisualizer.kt"
$content = Get-Content $hitbox -Raw
$replacement = @"
    fun toggle(): Boolean {
        while (true) {
            val current = _isEnabled.get()
            if (_isEnabled.compareAndSet(current, !current)) return !current
        }
    }
"@
$content = $content -replace '(?m)^\s*fun toggle\(\): Boolean = _isEnabled\.updateAndGet \{ !it \}\s*$', $replacement
Set-Content $hitbox $content -Encoding UTF8

Write-Host "Fixes applied."
