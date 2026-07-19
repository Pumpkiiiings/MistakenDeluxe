$basePath = "C:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken"

# Fix ObservantEXE
$file = "$basePath\game\entities\ObservantEXE.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'ejecutarDaÃ±o', 'ejecutarDano'
$content = $content -replace 'ejecutarDaÃ', 'ejecutarDano'
$content = $content -replace 'ejecutarDaño', 'ejecutarDano'
Set-Content $file $content -Encoding UTF8

# Fix FakeNPCAPI
$file = "$basePath\packet\fake\FakeNPCAPI.kt"
$content = Get-Content $file -Raw
$content = $content -replace 'val spawnPacket = WrapperPlayServerSpawnEntity', 'val spawnPacket = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity'
Set-Content $file $content -Encoding UTF8

Write-Host "Fixes applied."
