$base_path = "c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\resources\langs\es\"
$files = Get-ChildItem -Path $base_path -Filter "*.yml"

foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    $encoding = [System.Text.Encoding]::GetEncoding(1252)
    $text = $encoding.GetString($bytes)
    
    $text = $text.Replace([string][char]0x81, "")
    
    [System.IO.File]::WriteAllText($file.FullName, $text, [System.Text.Encoding]::UTF8)
    Write-Host "Converted $($file.Name) to UTF-8"
}
