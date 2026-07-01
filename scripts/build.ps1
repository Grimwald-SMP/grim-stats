# Full release build (Windows / PowerShell): dashboard + mod + tests, then prints the jar path.
#
# Usage:  ./scripts/build.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

& "$root\gradlew.bat" build --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$version = (Select-String -Path "$root\gradle.properties" -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
$jar = Join-Path $root "mod\build\libs\grimstats-$version.jar"
Write-Host ""
Write-Host "Build complete:" -ForegroundColor Green
Write-Host "  $jar"
Write-Host "Drop it into a Fabric server's mods/ folder (alongside Fabric API)."
