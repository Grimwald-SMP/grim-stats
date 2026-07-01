# GrimStats dev loop (Windows / PowerShell).
#
# Runs the Fabric test server (mod API on http://127.0.0.1:8765) and the Vite dev server
# (hot-reloading dashboard on http://127.0.0.1:5173) side by side. The dashboard talks to the
# mod API via VITE_API_BASE (see dashboard/.env.development); CORS is allowed by the mod's
# default config.
#
# Usage:  ./scripts/dev.ps1
# Stop:   Ctrl+C (stops the dashboard; then close the server window)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Write-Host "Starting GrimStats test server (this rebuilds the mod + dashboard)..." -ForegroundColor Cyan
$server = Start-Process -FilePath "$root\gradlew.bat" -ArgumentList ':mod:runTestServer','--console=plain' `
    -WorkingDirectory $root -PassThru -NoNewWindow

try {
    Write-Host "Starting dashboard dev server on http://127.0.0.1:5173 ..." -ForegroundColor Cyan
    Push-Location "$root\dashboard"
    npm run dev
} finally {
    Pop-Location
    if ($server -and -not $server.HasExited) {
        Write-Host "Stopping test server..." -ForegroundColor Yellow
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    }
}
