param(
    [string]$Stage = 'E:\tools\probes\package\stage',
    [string]$HostDir = '',
    [string]$CoreDll = '',
    [string]$RedistDir = '',
    [switch]$RequireFreshCore
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$native = Join-Path $repo 'desktopNative'
if (-not $HostDir) { $HostDir = Join-Path $native 'build-release\Release' }
if (-not $CoreDll) { $CoreDll = Join-Path $native 'core\build\bin\mingwX64\releaseShared\fjcore.dll' }

function Fail([string]$text) {
    Write-Host "stage: FAILED: $text"
    exit 1
}

function Need([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "missing $path" }
    return (Get-Item -LiteralPath $path)
}

$stageFull = [System.IO.Path]::GetFullPath($Stage)
if ($stageFull.TrimEnd('\') -notmatch '\\stage$') { Fail "stage folder must end in \stage: $stageFull" }
if ($stageFull.StartsWith($repo, [System.StringComparison]::OrdinalIgnoreCase)) { Fail "stage folder must be outside the repository: $stageFull" }

$appVersionFile = Join-Path $repo 'sharedLogic\src\commonMain\kotlin\com\michelle\raouf\fetch_jet\core\update\AppVersion.kt'
$appVersionText = Get-Content -LiteralPath $appVersionFile -Raw
$m = [regex]::Match($appVersionText, 'const\s+val\s+APP_VERSION\s*=\s*"([^"]+)"')
if (-not $m.Success) { Fail "APP_VERSION not found in $appVersionFile" }
$appVersion = $m.Groups[1].Value
$m = [regex]::Match($appVersionText, 'const\s+val\s+EXTENSION_VERSION\s*=\s*"([^"]+)"')
if (-not $m.Success) { Fail "EXTENSION_VERSION not found in $appVersionFile" }
$extensionVersion = $m.Groups[1].Value

$extDir = Join-Path $repo 'extension'
$manifestJson = Get-Content -LiteralPath (Join-Path $extDir 'manifest.json') -Raw | ConvertFrom-Json
if ($manifestJson.version -ne $extensionVersion) {
    Fail "extension\manifest.json version $($manifestJson.version) != EXTENSION_VERSION $extensionVersion"
}

$exe = Need (Join-Path $HostDir 'FetchJet.exe')
$slint = Need (Join-Path $HostDir 'slint_cpp.dll')
$core = Need $CoreDll

$sourceRoots = @(
    (Join-Path $repo 'sharedLogic\src\commonMain'),
    (Join-Path $repo 'sharedLogic\src\nativeDesktopMain'),
    (Join-Path $repo 'sharedLogic\src\mingwX64Main'),
    (Join-Path $native 'core\src'),
    (Join-Path $native 'kmpLocalization-mingw'),
    (Join-Path $repo 'packages\kmp-localization')
)
$sourceFiles = @()
foreach ($root in $sourceRoots) {
    if (Test-Path -LiteralPath $root) {
        $sourceFiles += Get-ChildItem -LiteralPath $root -Recurse -File -Include *.kt, *.kts -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\build\\' -and $_.FullName -notmatch '\\(androidMain|iosMain|jvmMain|jvmAndroidMain|jvmTest|commonTest|androidUnitTest)\\' }
    }
}
$sourceFiles += Get-Item -LiteralPath (Join-Path $repo 'sharedLogic\build.gradle.kts'), (Join-Path $native 'core\build.gradle.kts')
$newestSource = $sourceFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$coreFresh = $core.LastWriteTime -ge $newestSource.LastWriteTime
if (-not $coreFresh) {
    $text = "fjcore.dll ($($core.LastWriteTime)) is OLDER than the newest core source $($newestSource.FullName) ($($newestSource.LastWriteTime)): link it again"
    if ($RequireFreshCore) { Fail $text }
    Write-Host "stage: WARNING: $text"
}
$hostSources = Get-ChildItem -LiteralPath (Join-Path $native 'host'), (Join-Path $native 'ui'), (Join-Path $native 'res') -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '\\build' }
$newestHost = $hostSources | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($newestHost -and $exe.LastWriteTime -lt $newestHost.LastWriteTime) {
    Write-Host "stage: WARNING: FetchJet.exe ($($exe.LastWriteTime)) is OLDER than $($newestHost.FullName) ($($newestHost.LastWriteTime)): run packaging\build-release.cmd again"
}

if (-not $RedistDir) {
    if ($env:VCToolsRedistDir) {
        $RedistDir = Get-ChildItem -LiteralPath (Join-Path $env:VCToolsRedistDir 'x64') -Directory -Filter 'Microsoft.VC*.CRT' -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $RedistDir) {
        $RedistDir = Get-ChildItem 'C:\Program Files\Microsoft Visual Studio\*\*\VC\Redist\MSVC\*\x64\Microsoft.VC*.CRT' -Directory -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
}
if (-not $RedistDir) { Fail 'VC runtime redist folder not found (pass -RedistDir)' }
$vcFiles = @('msvcp140.dll', 'vcruntime140.dll', 'vcruntime140_1.dll') | ForEach-Object { Need (Join-Path $RedistDir $_) }

$torrentRoot = Join-Path $native 'torrent'
$torrentDll = $null
if (Test-Path -LiteralPath $torrentRoot) {
    $torrentDll = Get-ChildItem -LiteralPath $torrentRoot -Recurse -File -Filter 'fjtorrent.dll' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

$extFiles = @('background.js', 'yt-core.js', 'content.js', 'popup.html', 'popup.js', 'options.html', 'options.js',
    'icon16.png', 'icon48.png', 'icon128.png', 'manifest.json', 'platform.js')
$extTrees = @('i18n', '_locales')
foreach ($f in $extFiles) { [void](Need (Join-Path $extDir $f)) }
foreach ($t in $extTrees) { if (-not (Test-Path -LiteralPath (Join-Path $extDir $t) -PathType Container)) { Fail "missing extension\$t" } }

$resTrees = @('fonts', 'emoji')
foreach ($t in $resTrees) {
    $resDir = Join-Path $PSScriptRoot "..\res\$t"
    if (-not (Get-ChildItem -LiteralPath $resDir -File -ErrorAction SilentlyContinue)) { Fail "missing desktopNative\res\$t" }
}

$licenseSources = @(
    @{ From = (Join-Path $repo 'LICENSE'); To = 'licenses\FetchJet-LICENSE.txt' },
    @{ From = (Join-Path $PSScriptRoot 'licenses\THIRD-PARTY.txt'); To = 'licenses\THIRD-PARTY.txt' },
    @{ From = (Join-Path $PSScriptRoot 'licenses\Apache-2.0.txt'); To = 'licenses\Apache-2.0.txt' },
    @{ From = 'E:\tools\slint\licenses\LICENSE.md'; To = 'licenses\slint\LICENSE.md' },
    @{ From = 'E:\tools\slint\licenses\THIRDPARTY.md'; To = 'licenses\slint\THIRDPARTY.md' }
)
foreach ($l in $licenseSources) { [void](Need $l.From) }

if (Test-Path -LiteralPath $stageFull) { Remove-Item -LiteralPath $stageFull -Recurse -Force }
[void](New-Item -ItemType Directory -Path $stageFull)

function Put([string]$from, [string]$relative) {
    $to = Join-Path $stageFull $relative
    $dir = Split-Path -Parent $to
    if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Path $dir -Force) }
    Copy-Item -LiteralPath $from -Destination $to -Force
}

Put $exe.FullName 'FetchJet.exe'
Put $slint.FullName 'slint_cpp.dll'
Put $core.FullName 'fjcore.dll'
foreach ($v in $vcFiles) { Put $v.FullName $v.Name }
if ($torrentDll) { Put $torrentDll.FullName 'fjtorrent.dll' }
foreach ($f in $extFiles) { Put (Join-Path $extDir $f) "extension\chromium\$f" }
foreach ($t in $extTrees) {
    $base = Join-Path $extDir $t
    Get-ChildItem -LiteralPath $base -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($base.Length).TrimStart('\')
        Put $_.FullName "extension\chromium\$t\$rel"
    }
}
foreach ($l in $licenseSources) { Put $l.From $l.To }
foreach ($t in $resTrees) {
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "..\res\$t") -File | ForEach-Object { Put $_.FullName "$t\$($_.Name)" }
}
[System.IO.File]::WriteAllBytes((Join-Path $stageFull 'FetchJet.installed'), [byte[]]@())

$all = Get-ChildItem -LiteralPath $stageFull -Recurse -File
$total = ($all | Measure-Object Length -Sum).Sum
$listFile = Join-Path (Split-Path -Parent $stageFull) 'stage-files.txt'
$all | ForEach-Object { "{0}`t{1}" -f $_.FullName.Substring($stageFull.Length + 1), $_.Length } |
    Set-Content -LiteralPath $listFile -Encoding UTF8

Write-Host "stage: APP_VERSION $appVersion, EXTENSION_VERSION $extensionVersion"
Write-Host "stage: FetchJet.exe   $($exe.FullName) ($($exe.Length) bytes, $($exe.LastWriteTime))"
Write-Host "stage: slint_cpp.dll  $($slint.FullName) ($($slint.Length) bytes)"
Write-Host "stage: fjcore.dll     $($core.FullName) ($($core.Length) bytes, $($core.LastWriteTime)); newest core source $($newestSource.LastWriteTime): $(if ($coreFresh) { 'up to date' } else { 'OLDER' })"
Write-Host "stage: VC runtime     $RedistDir"
if ($torrentDll) { Write-Host "stage: fjtorrent.dll  $($torrentDll.FullName) ($($torrentDll.LastWriteTime))" } else { Write-Host 'stage: fjtorrent.dll  not found under desktopNative\torrent, not staged' }
Write-Host ("stage: {0} files, {1:N0} bytes in {2}" -f $all.Count, $total, $stageFull)
Write-Host "stage: file list $listFile"
Write-Host 'stage: ok'
exit 0
