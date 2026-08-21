$rootDir = "C:\Users\L900m\OneDrive\Desktop\Proyectos\Mistaken\MistakenDeluxe\MistakenDeluxe"
$oldPkgPath = Join-Path $rootDir "MistakenDeluxe-Core\src\main\java\liric\mistaken\characters"
$newPkgPath = Join-Path $rootDir "MistakenDeluxe-Core\src\main\java\liric\mistaken\models"

if (Test-Path $oldPkgPath) {
    Write-Host "Renaming $oldPkgPath to $newPkgPath"
    Rename-Item -Path $oldPkgPath -NewName "models"
} else {
    Write-Host "Directory $oldPkgPath not found. Skipping rename."
}

$oldStr = "liric.mistaken.characters"
$newStr = "liric.mistaken.models"
$changedFiles = 0

Get-ChildItem -Path $rootDir -Recurse -Include *.kt,*.java | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    if ($content -match $oldStr) {
        $content = $content -replace $oldStr, $newStr
        [IO.File]::WriteAllText($_.FullName, $content, [System.Text.Encoding]::UTF8)
        $changedFiles++
    }
}

Write-Host "Successfully updated $changedFiles files."
