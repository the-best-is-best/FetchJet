param(
    [string]$Tools = 'E:\tools\legacy',
    [string]$Win7Dlls = 'E:\tools\legacy\vm\win7dlls'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$legacy = $PSScriptRoot
$packaging = Join-Path $legacy '..\packaging'
$ci = Join-Path $Tools 'ci'
$msvc = Join-Path $Tools 'msvc\VC\Tools\MSVC\14.44.35207'
$redist = Join-Path $Tools 'msvc\VC\Redist\MSVC\14.44.35112\x64\Microsoft.VC143.CRT'

function Fail([string]$text) {
    Write-Host "::error::ci-legacy: $text"
    exit 1
}

function RunCmd([string]$script) {
    & cmd /c "`"$script`""
    if ($LASTEXITCODE -ne 0) { Fail "$([System.IO.Path]::GetFileName($script)) failed" }
}

function ShowLog([string]$log) {
    if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 80 }
}

foreach ($need in @((Join-Path $msvc 'bin\Hostx64\x64\cl.exe'), $redist, $Win7Dlls)) {
    if (-not (Test-Path -LiteralPath $need)) { Fail "missing $need (the Windows 7 tool set lives on the self-hosted Windows PC)" }
}

$env:FJ_LEGACY_TOOLS = $Tools
if (Test-Path -LiteralPath $ci) { Remove-Item -LiteralPath $ci -Recurse -Force }
New-Item -ItemType Directory -Force -Path $ci | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $Tools 'slint\lib\slint_cpp.dll'))) {
    Write-Host 'Slint for Windows 7: building'
    & cmd /c "`"$(Join-Path $legacy 'build-slint.cmd')`""
    if ($LASTEXITCODE -ne 0) { ShowLog (Join-Path $Tools 'build-slint.log'); Fail 'build-slint.cmd failed' }
} else {
    Write-Host "Slint for Windows 7: using $Tools\slint"
}

$env:FJT_BUILD_DIR = Join-Path $ci 'fjtorrent-build'
RunCmd (Join-Path $legacy 'build-torrent.cmd')

& cmd /c "`"$(Join-Path $legacy 'build-qjs.cmd')`""
if ($LASTEXITCODE -ne 0) { ShowLog (Join-Path $Tools 'build-qjs.log'); Fail 'build-qjs.cmd failed' }

$hostBuild = Join-Path $ci 'host-build'
$env:FJ_LEGACY_BUILD = $hostBuild
$env:FJ_LEGACY_TORRENT = Join-Path $env:FJT_BUILD_DIR 'fjtorrent.dll'
& cmd /c "`"$(Join-Path $legacy 'build-host.cmd')`""
if ($LASTEXITCODE -ne 0) { ShowLog (Join-Path $Tools 'build-host.log'); Fail 'build-host.cmd failed' }

$stage = Join-Path $ci 'stage'
$out = Join-Path $ci 'out'
New-Item -ItemType Directory -Force -Path $out | Out-Null
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $packaging 'stage.ps1') -Legacy -RequireFreshCore -Stage $stage -HostDir $hostBuild `
    -RedistDir $redist -QuickJs (Join-Path $Tools 'qjs-build\qjs.exe')
if ($LASTEXITCODE -ne 0) { Fail 'stage.ps1 -Legacy failed' }
foreach ($f in @('fjtorrent.dll', 'qjs.exe', 'FetchJet.legacy')) {
    if (-not (Test-Path -LiteralPath (Join-Path $stage $f))) { Fail "$f is not in the Windows 7 stage folder" }
}

$env:FJ_DUMPBIN = Join-Path $msvc 'bin\Hostx64\x64\dumpbin.exe'
$check = @('FetchJet.exe', 'fjcore.dll', 'fjtorrent.dll', 'slint_cpp.dll', 'qjs.exe') | ForEach-Object { Join-Path $stage $_ }
& python -I (Join-Path $legacy 'win7check.py') $Win7Dlls $stage @check
if ($LASTEXITCODE -ne 0) { Fail 'a file of the Windows 7 build uses a function Windows 7 does not have' }

$env:FJ_STAGE = $stage
$env:FJ_OUT = $out
$env:FJ_LEGACY = '1'
& cmd /c "`"$(Join-Path $packaging 'build-msi.cmd')`""
if ($LASTEXITCODE -ne 0) { ShowLog (Join-Path $out 'build-msi.log'); Fail 'build-msi.cmd failed' }
$version = "$(& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $packaging 'version.ps1'))".Trim()
if (-not $version) { Fail 'could not read APP_VERSION' }
$msi = Join-Path $out "FetchJet-$version-Windows-7-8-8.1.msi"
if (-not (Test-Path -LiteralPath $msi)) { Fail "no $msi" }
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $packaging 'check-msi.ps1') -Msi $msi -Stage $stage -Scratch 'E:\tools\probes\package\check-msi-legacy'
if ($LASTEXITCODE -ne 0) { Fail 'check-msi.ps1 failed for the Windows 7 MSI' }

& cmd /c "`"$(Join-Path $packaging 'build-setup.cmd')`""
if ($LASTEXITCODE -ne 0) { ShowLog (Join-Path $out 'build-setup.log'); Fail 'build-setup.cmd failed' }
$setup = Join-Path $out "FetchJet-$version-Setup-Windows-7-8-8.1.exe"
if (-not (Test-Path -LiteralPath $setup)) { Fail "no $setup" }
Write-Host ("{0}  {1:N0} bytes" -f $setup, (Get-Item -LiteralPath $setup).Length)
exit 0
