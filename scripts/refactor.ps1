$targetDir = "C:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken"
$files = Get-ChildItem -Path $targetDir -Filter "*.kt" -Recurse

$pattern = '(?:\w+\.)?world\.spawn\(([^,]+),\s*(BlockDisplay|ItemDisplay|TextDisplay)::class\.java\)\s*\{'

foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw

    if ($content -match $pattern) {
        $viewersCode = "org.bukkit.Bukkit.getOnlinePlayers().toList()"
        
        if ($file.FullName -match "roles\\killers" -or $file.FullName -match "roles\\survivors") {
            $viewersCode = "org.bukkit.plugin.java.JavaPlugin.getPlugin(liric.mistaken.Mistaken::class.java).sessionManager.getSession(player)?.getPlayers() ?: listOf(player)"
        } elseif ($file.FullName -match "game\\entities") {
            $viewersCode = "org.bukkit.Bukkit.getOnlinePlayers().toList()"
        } elseif ($file.FullName -match "GeneratorManager.kt") {
            $viewersCode = "org.bukkit.plugin.java.JavaPlugin.getPlugin(liric.mistaken.Mistaken::class.java).sessionManager.activeSessions.values.find { it.mapName == state.mapName }?.getPlayers() ?: org.bukkit.Bukkit.getOnlinePlayers().toList()"
        }

        # Use powershell regex replacement
        # $1 is the location, $2 is the Display type
        $replacement = 'liric.mistaken.packet.PacketFactory.displays.build$2(' + $viewersCode + ', $1) {'
        
        $newContent = [regex]::Replace($content, $pattern, $replacement)
        
        if ($newContent -ne $content) {
            Set-Content -Path $file.FullName -Value $newContent -Encoding UTF8
            Write-Host "Refactored: $($file.FullName)"
        }
    }
}
Write-Host "Done!"
