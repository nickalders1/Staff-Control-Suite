# build-and-sign.ps1
# Builds the Staff Control Suite desktop app, packages it with Inno Setup,
# and optionally publishes a GitHub release.
#
# Usage:
#   .\build-and-sign.ps1                        # build only
#   .\build-and-sign.ps1 -Version 1.2.0         # set version
#   .\build-and-sign.ps1 -Version 1.2.0 -Release # build + push GitHub release

param(
    [string] $Version       = "1.0.0",
    [string] $InnoSetupPath = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    [switch] $Release                            # publish a GitHub release
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
# Strips the Zone.Identifier ADS so recipients don't get a SmartScreen popup.
Write-Host ""
Write-Host "==> Step 3: Removing Mark of the Web from installer" -ForegroundColor Yellow
Unblock-File -Path $InstallerExe
Write-Host "  Done - no SmartScreen popup for recipients." -ForegroundColor Green

# --- Step 4: Publish GitHub release (optional) ---
if ($Release) {
    Write-Host ""
    Write-Host "==> Step 4: Publishing GitHub release v$Version" -ForegroundColor Yellow

    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh) {
        Write-Warning "GitHub CLI (gh) not found - skipping release. Install from https://cli.github.com"
    } else {
        # Create a git tag for this version
        git tag "v$Version"
        git push origin "v$Version"

        # Create the GitHub release and upload the installer
        gh release create "v$Version" $InstallerExe `
            --title "Staff Control Suite v$Version" `
            --notes "Release v$Version"

        Write-Host "  GitHub release published: v$Version" -ForegroundColor Green
    }
} else {
    Write-Host ""
    Write-Host "  Tip: run with -Release to publish a GitHub release automatically" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "==> Done!" -ForegroundColor Green
Write-Host "    Executable : $AppExe"
Write-Host "    Installer  : $InstallerExe"
