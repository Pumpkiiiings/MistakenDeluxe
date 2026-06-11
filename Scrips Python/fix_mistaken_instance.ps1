$files = @(
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\supervivientes\clases\Aldeano.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\supervivientes\clases\Jesse.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\supervivientes\clases\Minty.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\supervivientes\clases\RaincoatKid.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\supervivientes\clases\Repartidor.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\supervivientes\clases\Troll.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\supervivientes\clases\Notch.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\supervivientes\clases\KasaneTeto.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\supervivientes\clases\Civil.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\asesinos\clases\CharlieInferno.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\asesinos\clases\ColorAndElectricity.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\asesinos\clases\Herobrine.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\asesinos\clases\NullAsesino.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\asesinos\clases\Slasher.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\asesinos\clases\Sowoul.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\asesinos\clases\Romeo.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\asesinos\clases\Mariachi.kt",
    "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken\roles\asesinos\clases\Entity303.kt"
)

foreach ($file in $files) {
    if (Test-Path $file) {
        $content = Get-Content $file -Raw -Encoding UTF8
        $newContent = $content.Replace('Mistaken.instance.pumpking.lib.service.PumpkingServiceManager', 'pumpking.lib.service.PumpkingServiceManager')
        if ($content -ne $newContent) {
            Set-Content $file -Value $newContent -Encoding UTF8
            Write-Host "Fixed Mistaken.instance prefix in $file"
        }
    }
}
