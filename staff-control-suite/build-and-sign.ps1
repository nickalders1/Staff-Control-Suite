# build-and-sign.ps1
# Builds the Staff Control Suite desktop app and packages it with Inno Setup.
# The output installer has the Mark of the Web removed so recipients don't get SmartScreen popups.
#
# Usage:
#   .\build-and-sign.ps1
#   .\build-and-sign.ps1 -Version 1.2.0

param(
    [string] $Version       = "1.0.0",
    [string] $InnoSetupPath = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root         = $PSScriptRoot
$AppDir       = Join-Path $Root "desktop-app"
$PublishDir   = Join-Path $AppDir "publish"
$InstallerDir = Join-Path $Root "installer"
$IssScript    = Join-Path $InstallerDir "StaffControlSuite.iss"
$AppExe       = Join-Path $PublishDir "StaffControlSuite.exe"
$InstallerExe = Join-Path $InstallerDir "dist\StaffControlSuite-Setup-$Version.exe"

# --- Step 1: dotnet publish ---
Write-Host ""
Write-Host "==> Step 1: Building desktop app (Release / win-x64 / self-contained)" -ForegroundColor Yellow

Push-Location $AppDir
try {
    dotnet publish -c Release -r win-x64 --self-contained true `
        -p:PublishSingleFile=true `
        -p:IncludeNativeLibrariesForSelfExtract=true `
        -p:Version=$Version `
        -p:FileVersion=$Version `
        -o publish

    if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

Write-Host "  Build output: $PublishDir" -ForegroundColor Green

# --- Step 2: Build installer with Inno Setup ---
Write-Host ""
Write-Host "==> Step 2: Building installer with Inno Setup" -ForegroundColor Yellow

if (-not (Test-Path $InnoSetupPath)) {
    throw "Inno Setup not found at: $InnoSetupPath -- install from https://jrsoftware.org/isinfo.php or pass -InnoSetupPath"
}

& $InnoSetupPath /DAppVersion=$Version $IssScript

if ($LASTEXITCODE -ne 0) { throw "Inno Setup compiler failed with exit code $LASTEXITCODE" }

Write-Host "  Installer: $InstallerExe" -ForegroundColor Green

# --- Step 3: Remove Mark of the Web ---
Write-Host ""
Write-Host "==> Step 3: Removing Mark of the Web from installer" -ForegroundColor Yellow
Unblock-File -Path $InstallerExe
Write-Host "  Done - no SmartScreen popup for recipients." -ForegroundColor Green

Write-Host ""
Write-Host "==> Done!" -ForegroundColor Green
Write-Host "    Executable : $AppExe"
Write-Host "    Installer  : $InstallerExe"
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Upload $InstallerExe to your GitHub release (via github.com)"
Write-Host "  2. Copy the download link and update your version Gist"
Write-Host "     Gist format: { version: '$Version', downloadUrl: 'PASTE_LINK_HERE' }"
