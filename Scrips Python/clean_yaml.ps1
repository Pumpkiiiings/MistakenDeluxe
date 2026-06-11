$base_path = "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\resources\langs\es\"
$files = Get-ChildItem -Path $base_path -Filter "*.yml"

foreach ($file in $files) {
    # Read raw bytes
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    
    # Remove 0x81
    $newBytes = New-Object System.Collections.Generic.List[byte]
    foreach ($b in $bytes) {
        if ($b -ne 129) {
            $newBytes.Add($b)
        }
    }
    
    # Save without 0x81
    [System.IO.File]::WriteAllBytes($file.FullName, $newBytes.ToArray())
    
    # Read as UTF8 (it will turn invalid bytes into Replacement Character \uFFFD)
    $text = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    
    # The replacement character in powershell is [char]0xFFFD
    $rep = [char]0xFFFD
    
    # Now replace the corrupted words!
    $text = $text.Replace("INFORMACI$rep`"N", "INFORMACIÓN")
    $text = $text.Replace("INFORMACI${rep}N", "INFORMACIÓN")
    $text = $text.Replace("estad${rep}sticas", "estadísticas")
    $text = $text.Replace("Mecǭnica", "Mecánica")
    $text = $text.Replace("clǭsico", "clásico")
    $text = $text.Replace("fsica", "física")
    $text = $text.Replace("mgico", "mágico")
    $text = $text.Replace("Fǭcil", "Fácil")
    $text = $text.Replace("persecucin", "persecución")
    $text = $text.Replace("Dif${rep}cil", "Difícil")
    $text = $text.Replace("Mgica", "Mágica")
    $text = $text.Replace("T${rep}nel", "Túnel")
    $text = $text.Replace("${rep}ltimo", "Último")
    $text = $text.Replace("M${rep}sica", "Música")
    $text = $text.Replace("Da${rep}o", "Daño")
    
    # Write back as valid UTF-8
    [System.IO.File]::WriteAllText($file.FullName, $text, [System.Text.Encoding]::UTF8)
    Write-Host "Cleaned $($file.Name)"
}
