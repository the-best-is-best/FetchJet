# One-shot setup of a GitHub Actions self-hosted runner on this Windows PC.
# It builds the Windows installers (MSI/EXE) and the Android APK/AAB.
#
# Run in PowerShell (normal, not admin):
#   powershell -ExecutionPolicy Bypass -File E:\tbib\FetchJet\scripts\setup-windows-runner.ps1
#
# Optional: -Token <registration token from GitHub>; otherwise it is fetched with gh.
param([string]$Token = "")

$ErrorActionPreference = "Stop"
$Repo  = "the-best-is-best/FetchJet"
$Label = "win-host"
$Dir   = "C:\ar"          # short path: Gradle/Android hit the 260-char limit in deep folders

function Say($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Have($cmd) { [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }

Say "1/6 Tools (Git, JDK 21, WiX 3 for the MSI)"
if (-not (Have git)) {
    winget install --id Git.Git -e --accept-source-agreements --accept-package-agreements
}
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Where-Object Name -like "jdk-21*" | Select-Object -First 1
if (-not $jdk) {
    winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-source-agreements --accept-package-agreements
    $jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like "jdk-21*" | Select-Object -First 1
}
$wix = Get-ChildItem "C:\Program Files (x86)" -Directory -Filter "WiX Toolset v3*" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $wix) {
    winget install --id WiXToolset.WiXToolset -e --accept-source-agreements --accept-package-agreements
    $wix = Get-ChildItem "C:\Program Files (x86)" -Directory -Filter "WiX Toolset v3*" | Select-Object -First 1
}
Write-Host "JDK: $($jdk.FullName)"
Write-Host "WiX: $($wix.FullName)"

Say "2/6 Android SDK"
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path $sdk)) {
    Write-Host "Android SDK not found at $sdk." -ForegroundColor Yellow
    Write-Host "Install Android Studio once (it installs the SDK there), then run this script again."
    exit 1
}
Write-Host "SDK: $sdk"

Say "3/6 Registration token"
if (-not $Token) {
    $gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
    if (-not $gh -and (Test-Path "C:\Program Files\GitHub CLI\gh.exe")) { $gh = "C:\Program Files\GitHub CLI\gh.exe" }
    if ($gh) {
        try { $Token = (& $gh api -X POST "repos/$Repo/actions/runners/registration-token" --jq .token).Trim() } catch {}
    }
}
if (-not $Token) {
    Write-Host "Open https://github.com/$Repo/settings/actions/runners/new and copy the value after --token"
    $Token = Read-Host "Paste the token"
}

Say "4/6 Runner download"
New-Item -ItemType Directory -Force -Path $Dir | Out-Null
Set-Location $Dir
if (-not (Test-Path ".\config.cmd")) {
    $rel = Invoke-RestMethod "https://api.github.com/repos/actions/runner/releases/latest"
    $ver = $rel.tag_name.TrimStart("v")
    $zip = "actions-runner-win-x64-$ver.zip"
    Invoke-WebRequest "https://github.com/actions/runner/releases/download/v$ver/$zip" -OutFile $zip
    Expand-Archive $zip -DestinationPath . -Force
    Remove-Item $zip
}

Say "5/6 Register (label: $Label)"
if (-not (Test-Path ".\.runner")) {
    .\config.cmd --unattended --url "https://github.com/$Repo" --token $Token `
        --name "win-$env:COMPUTERNAME" --labels $Label --work _work --replace
} else {
    Write-Host "already registered"
}
# What the jobs see: JDK, Android SDK, WiX and Git Bash, whatever the session's PATH is.
$gitBin = "C:\Program Files\Git\bin"
@"
JAVA_HOME=$($jdk.FullName)
ANDROID_HOME=$sdk
ANDROID_SDK_ROOT=$sdk
PATH=$($jdk.FullName)\bin;$($wix.FullName)\bin;$gitBin;$env:PATH
"@ | Set-Content -Encoding ASCII ".\.env"

Say "6/6 Start now and at every logon"
$action  = New-ScheduledTaskAction -Execute "$Dir\run.cmd" -WorkingDirectory $Dir
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$set     = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -Hidden
Register-ScheduledTask -TaskName "GitHub Runner FetchJet" -Action $action -Trigger $trigger -Settings $set -Force | Out-Null
Start-ScheduledTask -TaskName "GitHub Runner FetchJet"

Write-Host ""
Write-Host "Done. Last step on GitHub (once): https://github.com/$Repo/settings/variables/actions" -ForegroundColor Green
Write-Host "  RUNNER_WINDOWS = $Label"
Write-Host "  RUNNER_ANDROID = $Label"
Write-Host "Check it shows Idle: https://github.com/$Repo/settings/actions/runners"
