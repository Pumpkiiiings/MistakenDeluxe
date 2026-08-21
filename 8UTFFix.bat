@echo off
echo ========================================================
echo Mistaken Deluxe UTF-8 Fixer
echo ========================================================
echo El problema de los caracteres corruptos (Mojibake) ocurrio
echo exclusivamente porque un script de PowerShell modifico los
echo archivos sin usar -Encoding UTF8.
echo.
echo Ya he reparado permanentemente el error restaurando los
echo archivos desde Git y volviendo a aplicar los cambios
echo nativamente usando Java/Python con UTF-8 seguro.
echo.
echo Para evitar problemas de codificacion en el futuro,
echo Kotlin/Java en Gradle ya estan configurados para usar UTF-8
echo en build.gradle.kts.
echo.
echo Si en algun momento vuelves a hacer scripts en PowerShell,
echo SIEMPRE agrega: -Encoding UTF8 a Set-Content / Out-File.
echo.
pause
