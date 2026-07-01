# Runs every test suite (Windows / PowerShell): mod JUnit tests + dashboard Vitest.
#
# Usage:  ./scripts/test.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Write-Host "Mod tests (JUnit)..." -ForegroundColor Cyan
& "$root\gradlew.bat" :mod:test --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Dashboard tests (Vitest)..." -ForegroundColor Cyan
Push-Location "$root\dashboard"
try {
    npx vitest run
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

Write-Host "All tests passed." -ForegroundColor Green
