$files = @("messages.yml", "asesinos_info.yml", "supervivientes_info.yml")
$base_path = "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\resources\langs\es\"

foreach ($f in $files) {
    $path = $base_path + $f
    $bytes = [System.IO.File]::ReadAllBytes($path)
    
    # Remove 0x81
    $newBytes = New-Object System.Collections.Generic.List[byte]
    $found = $false
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        if ($bytes[$i] -eq 129) {
            $found = $true
        } else {
            $newBytes.Add($bytes[$i])
        }
    }
    
    if ($found) {
        Write-Host "Removed 0x81 from $f"
        [System.IO.File]::WriteAllBytes($path, $newBytes.ToArray())
    }
    
    # Fix text replacements
    $text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $text = $text.Replace("clǭsico", "clásico")
    $text = $text.Replace([char]0xC7 + [char]0xAD, "á") # ǭ fallback
    $text = $text.Replace("fsica", "física")
    $text = $text.Replace("mgico", "mágico")
    $text = $text.Replace("persecucin", "persecución")
    $text = $text.Replace("Fǭcil", "Fácil")
    
    [System.IO.File]::WriteAllText($path, $text, [System.Text.Encoding]::UTF8)
    Write-Host "Fixed text in $f"
}
