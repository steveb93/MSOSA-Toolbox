# Bumps <revision> in msosa-model-exporter/pom.xml via the Maven Versions Plugin,
# then propagates the new version into artefact references inside repo .md files.
#
# Only artefact-prefixed forms are replaced (msosa-model-exporter-X.Y.Z.jar and
# msosa-model-exporter-X.Y.Z-plugin.zip), so prose like "UAF 1.2" or "MSOSA 2022x"
# stays untouched. Any drift between pom and docs is self-healed because the docs
# regex matches whatever version is currently there.
#
# Usage:
#   .\bump-version.ps1 -Version 1.3.2-Preview
#   .\bump-version.ps1 -Version 1.8.0 -DryRun

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^\d+\.\d+\.\d+(-[A-Za-z0-9]+)?$')]
    [string]$Version,

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$pomDir   = Join-Path $repoRoot 'msosa-model-exporter'
$pomPath  = Join-Path $pomDir 'pom.xml'

if (-not (Test-Path $pomPath)) {
    throw "pom.xml not found at $pomPath"
}

# Read current <revision> for reporting only — the doc regex doesn't depend on it.
$pomText = Get-Content $pomPath -Raw
if ($pomText -notmatch '<revision>([^<]+)</revision>') {
    throw "Could not find <revision> property in $pomPath"
}
$oldVersion = $Matches[1]

Write-Host ""
if ($oldVersion -eq $Version) {
    Write-Host "pom.xml already at $Version (will still sweep docs for drift)." -ForegroundColor Yellow
} else {
    Write-Host "Bumping: $oldVersion -> $Version" -ForegroundColor Cyan
}
Write-Host ""

# --- 1. pom.xml via Maven ---------------------------------------------------
Write-Host "[1/2] mvn versions:set-property" -ForegroundColor Cyan
$mvnArgs = @(
    'versions:set-property'
    '-Dproperty=revision'
    "-DnewVersion=$Version"
    '-DgenerateBackupPoms=false'
)
if ($DryRun) {
    Write-Host "      (dry-run) mvn $($mvnArgs -join ' ')" -ForegroundColor Gray
} elseif ($oldVersion -eq $Version) {
    Write-Host "      skip (no change needed)" -ForegroundColor Gray
} else {
    Push-Location $pomDir
    try {
        & mvn @mvnArgs
        if ($LASTEXITCODE -ne 0) {
            throw "mvn versions:set-property failed (exit $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }
}

# --- 2. .md files -----------------------------------------------------------
Write-Host ""
Write-Host "[2/2] Updating doc artefact references" -ForegroundColor Cyan

$docFiles = @(
    (Join-Path $repoRoot 'CLAUDE.md')
    (Join-Path $pomDir   'CLAUDE.md')
    (Join-Path $pomDir   'README.md')
)

# msosa-model-exporter-<ver> followed by .jar or -plugin.zip
$pattern     = 'msosa-model-exporter-\d+\.\d+\.\d+(-[A-Za-z0-9]+)?(?=(\.jar|-plugin\.zip))'
$replacement = "msosa-model-exporter-$Version"

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
$totalHits = 0

foreach ($file in $docFiles) {
    if (-not (Test-Path $file)) {
        Write-Warning "      skip (not found): $file"
        continue
    }
    $content = [System.IO.File]::ReadAllText($file)
    $hits    = [regex]::Matches($content, $pattern)
    if ($hits.Count -eq 0) { continue }

    $newContent = [regex]::Replace($content, $pattern, $replacement)
    $rel        = $file.Substring($repoRoot.Length).TrimStart('\', '/')
    $suffix     = if ($hits.Count -eq 1) { 'match' } else { 'matches' }
    Write-Host "      $rel  ($($hits.Count) $suffix)" -ForegroundColor Green

    if (-not $DryRun) {
        [System.IO.File]::WriteAllText($file, $newContent, $utf8NoBom)
    }
    $totalHits += $hits.Count
}

if ($totalHits -eq 0) {
    Write-Host "      No artefact references found in docs." -ForegroundColor Yellow
}

Write-Host ""
if ($DryRun) {
    Write-Host "Dry run complete. Re-run without -DryRun to apply." -ForegroundColor Yellow
} else {
    Write-Host "Done. Review with: git diff" -ForegroundColor Green
}
