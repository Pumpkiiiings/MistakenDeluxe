$base_path = "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\resources\langs\es\"
$files = Get-ChildItem -Path $base_path -Filter "*.yml"

foreach ($file in $files) {
    # Read raw bytes (which are in Windows-1252)
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    
    # 1. Remove 0x81 byte which crashes snakeyaml
    $newBytes = New-Object System.Collections.Generic.List[byte]
    foreach ($b in $bytes) {
        if ($b -ne 129) {
            $newBytes.Add($b)
        }
    }
    $bytes = $newBytes.ToArray()

    # 2. Decode from Windows-1252 to String
    $encoding = [System.Text.Encoding]::GetEncoding(1252)
    $text = $encoding.GetString($bytes)
    
    # 3. Fix the weird 'clǭsico' and missing letters (the original author typed them weirdly)
    # The 'ǭ' char in 1252 decoded to string will look like 'ǭ' or maybe two characters
    $text = $text.Replace("clǭsico", "clásico")
    $text = $text.Replace("Mecǭnica", "Mecánica")
    $text = $text.Replace("Fǭcil", "Fácil")
    $text = $text.Replace("persecucin", "persecución")
    $text = $text.Replace("fsica", "física")
    $text = $text.Replace("mgico", "mágico")
    $text = $text.Replace("Difcil", "Difícil")
    $text = $text.Replace("Mgica", "Mágica")

    # The characters like Ó, í, á that were written in 1252 will be decoded perfectly to string!
    # 4. Save the string as UTF-8
    [System.IO.File]::WriteAllText($file.FullName, $text, [System.Text.Encoding]::UTF8)
    Write-Host "Perfectly cleaned $($file.Name)"
}
