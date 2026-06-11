$bytes = [System.IO.File]::ReadAllBytes("c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\resources\langs\es\messages.yml")
$bad = $false
for($i=0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -eq 129) {
        Write-Host "Found 0x81 at offset $i"
        $bad = $true
    }
}
if ($bad) {
    # Replace 0x81 with a space (0x20)
    for($i=0; $i -lt $bytes.Length; $i++) {
        if ($bytes[$i] -eq 129) {
            $bytes[$i] = 32
        }
    }
    [System.IO.File]::WriteAllBytes("c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\resources\langs\es\messages.yml", $bytes)
    Write-Host "Fixed file!"
} else {
    Write-Host "No 0x81 byte found in messages.yml"
}
