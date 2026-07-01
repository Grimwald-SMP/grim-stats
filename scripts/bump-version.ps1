# Bumps the mod version everywhere it lives (Windows / PowerShell).
#
# Updates:
#   - gradle.properties        mod_version
#   - dashboard/package.json   version (kept in lockstep with the mod)
#   - CHANGELOG.md             turns the Unreleased section into the new release
#
# Usage:
#   ./scripts/bump-version.ps1 patch          0.4.0 -> 0.4.1
#   ./scripts/bump-version.ps1 minor          0.4.0 -> 0.5.0
#   ./scripts/bump-version.ps1 major          0.4.0 -> 1.0.0
#   ./scripts/bump-version.ps1 1.2.3          explicit version
#   ./scripts/bump-version.ps1 patch -Tag     also creates an annotated git tag v<version>
#
# Tagging and pushing the tag is what triggers the release workflow on GitHub.

param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [switch]$Tag
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradleProps = Join-Path $root 'gradle.properties'
$packageJson = Join-Path $root 'dashboard\package.json'
$changelog = Join-Path $root 'CHANGELOG.md'

# Windows PowerShell's `Set-Content -Encoding utf8` writes a BOM, which breaks strict JSON
# parsers (Vite refuses a BOM'd package.json). Write UTF-8 without BOM explicitly.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
function Write-NoBom([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, $utf8NoBom)
}

# --- current version -------------------------------------------------------
$propsText = Get-Content $gradleProps -Raw
if ($propsText -notmatch '(?m)^mod_version=(\d+)\.(\d+)\.(\d+)\s*$') {
    throw "Could not find mod_version=x.y.z in gradle.properties"
}
$current = "$($Matches[1]).$($Matches[2]).$($Matches[3])"
$major = [int]$Matches[1]; $minor = [int]$Matches[2]; $patch = [int]$Matches[3]

# --- next version ----------------------------------------------------------
switch -Regex ($Version) {
    '^major$' { $next = "$($major + 1).0.0" }
    '^minor$' { $next = "$major.$($minor + 1).0" }
    '^patch$' { $next = "$major.$minor.$($patch + 1)" }
    '^\d+\.\d+\.\d+$' { $next = $Version }
    default { throw "Version must be major, minor, patch, or x.y.z (got '$Version')" }
}
if ($next -eq $current) {
    throw "Version is already $current"
}

Write-Host "Bumping $current -> $next" -ForegroundColor Cyan

# --- gradle.properties -----------------------------------------------------
Write-NoBom $gradleProps ($propsText -replace '(?m)^mod_version=.*$', "mod_version=$next")

# --- dashboard/package.json ------------------------------------------------
if (Test-Path $packageJson) {
    $pkgText = [System.IO.File]::ReadAllText($packageJson)
    Write-NoBom $packageJson ($pkgText -replace '("version"\s*:\s*")[^"]+(")', "`${1}$next`${2}")
}

# --- CHANGELOG.md -----------------------------------------------------------
if (Test-Path $changelog) {
    $date = Get-Date -Format 'yyyy-MM-dd'
    $text = [System.IO.File]::ReadAllText($changelog)
    if ($text -match '(?m)^## Unreleased') {
        Write-NoBom $changelog ($text -replace '(?m)^## Unreleased\s*$', "## Unreleased`n`n## $next - $date")
    } else {
        Write-Host "CHANGELOG.md has no '## Unreleased' section; skipped." -ForegroundColor Yellow
    }
}

Write-Host "Updated gradle.properties, dashboard/package.json, CHANGELOG.md" -ForegroundColor Green

# --- git tag (optional) -----------------------------------------------------
if ($Tag) {
    if (-not (Test-Path (Join-Path $root '.git'))) {
        throw "-Tag requested but this is not a git repository"
    }
    git -C $root add gradle.properties dashboard/package.json CHANGELOG.md
    git -C $root commit -m "Release v$next"
    git -C $root tag -a "v$next" -m "GrimStats v$next"
    Write-Host "Committed and tagged v$next. Push with: git push && git push origin v$next" -ForegroundColor Green
} else {
    Write-Host "Next: review, commit, then tag with 'git tag -a v$next' to trigger a release." -ForegroundColor Cyan
}
