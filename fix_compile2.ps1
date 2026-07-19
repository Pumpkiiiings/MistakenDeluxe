$basePath = "C:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken"

# 1. PacketVisibilityListener
$file = "$basePath\game\managers\engine\visibility\PacketVisibilityListener.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'event.player as\? Player', '(event.getPlayer() as? Player)'
$content = $content -replace 'entry.userProfile.uuid', 'entry.profileId'
Set-Content $file $content -Encoding UTF8

# 2. PacketInteractListener
$file = "$basePath\packet\PacketInteractListener.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'event.player as\? Player', '(event.getPlayer() as? Player)'
Set-Content $file $content -Encoding UTF8

# 3. GeneratorManager
$file = "$basePath\game\managers\gameplay\GeneratorManager.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'org.bukkit.plugin.java.JavaPlugin.getPlugin\(liric.mistaken.Mistaken::class.java\).sessionManager.activeSessions.values.find \{ it.mapName == state.mapName \}\?.getPlayers\(\)', 'org.bukkit.Bukkit.getOnlinePlayers().toList()'
Set-Content $file $content -Encoding UTF8

# 4. KillerSkillListener
$file = "$basePath\listeners\killers\KillerSkillListener.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'plugin.configManager.getKillerConfig\(this.id\)', 'plugin.configManager.getKillerConfig(asesino.id)'
Set-Content $file $content -Encoding UTF8

# 5. SurvivorSkillListener
$file = "$basePath\listeners\survivors\SurvivorSkillListener.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'plugin.configManager.getSurvivorConfig\(this.id\)', 'plugin.configManager.getSurvivorConfig(clase.id)'
Set-Content $file $content -Encoding UTF8

# 6. KillerShop & SurvivorShop
$file = "$basePath\menu\menus\KillerShop.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'plugin.configManager.getKillerConfig\(this.id\)', 'plugin.configManager.getKillerConfig("global")'
Set-Content $file $content -Encoding UTF8

$file = "$basePath\menu\menus\SurvivorShop.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'plugin.configManager.getSurvivorConfig\(this.id\)', 'plugin.configManager.getSurvivorConfig("global")'
Set-Content $file $content -Encoding UTF8

# 7. FakeBlockAPI
$file = "$basePath\packet\fake\FakeBlockAPI.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'Vector3d\(location.x, location.y, location.z\)', 'com.github.retrooper.packetevents.util.Vector3i(location.blockX, location.blockY, location.blockZ)'
Set-Content $file $content -Encoding UTF8

# 8. FakeEntityAPI
$file = "$basePath\packet\fake\FakeEntityAPI.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'uuid, //', 'java.util.Optional.of(uuid), //'
Set-Content $file $content -Encoding UTF8

# 9. FakeNPCAPI
$file = "$basePath\packet\fake\FakeNPCAPI.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'uuid, //', 'java.util.Optional.of(uuid), //'
Set-Content $file $content -Encoding UTF8

# 10. ColorAndElectricity
$file = "$basePath\roles\killers\clases\ColorAndElectricity.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'org.bukkit.plugin.java.JavaPlugin.getPlugin\(liric.mistaken.Mistaken::class.java\).sessionManager.getSession\(player\)\?.getPlayers\(\) \?: listOf\(player\)', 'org.bukkit.Bukkit.getOnlinePlayers().toList()'
Set-Content $file $content -Encoding UTF8

Write-Host "Fixes applied."
