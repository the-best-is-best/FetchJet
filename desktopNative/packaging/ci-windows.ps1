param(
    [Parameter(Mandatory = $true)][ValidateSet('prepare', 'package')][string]$Step
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$packaging = $PSScriptRoot
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$slintDir = 'E:\tools\slint'
$ltDir = 'E:\tools\libtorrent-2.1.2'
$ltCommit = '6da363d2994f17c0b3c0450d124cf73a31a73847'
$defaultSlintUrl = 'https://github.com/slint-ui/slint/releases/download/v1.18.1/Slint-cpp-1.18.1-win64-MSVC-AMD64.exe'
$ciBase = 'E:\tools\probes\package\ci'
$curl = Join-Path $env:SystemRoot 'System32\curl.exe'
$tar = Join-Path $env:SystemRoot 'System32\tar.exe'

function Fail([string]$text) {
    Write-Host "::error::ci-windows: $text"
    exit 1
}

function AddEnv([string]$name, [string]$value) {
    Add-Content -LiteralPath $env:GITHUB_ENV -Value "$name=$value"
    Set-Item -Path "Env:$name" -Value $value
}

function AddPath([string]$dir) {
    Add-Content -LiteralPath $env:GITHUB_PATH -Value $dir
    $env:PATH = "$dir;$env:PATH"
}

function Download([string]$url, [string]$to) {
    & $curl -4 --retry 3 -fsSL -o $to $url
    if ($LASTEXITCODE -ne 0) { Fail "download failed: $url" }
}

function Ps([string]$script, [string[]]$arguments) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $packaging $script) @arguments
    if ($LASTEXITCODE -ne 0) { Fail "$script failed" }
}

function SlintMissing {
    $missing = @()
    foreach ($f in @('lib\cmake\Slint\SlintConfig.cmake', 'lib\slint_cpp.dll', 'licenses\LICENSE.md', 'licenses\THIRDPARTY.md')) {
        if (-not (Test-Path -LiteralPath (Join-Path $slintDir $f))) { $missing += $f }
    }
    return $missing
}

function PrepareDriveE {
    if (Test-Path -LiteralPath 'E:\') {
        Write-Host 'drive E: exists (self-hosted PC): using it'
    } else {
        $vhd = Join-Path $env:RUNNER_TEMP 'fj-e.vhdx'
        $script = Join-Path $env:RUNNER_TEMP 'fj-e.diskpart'
        Set-Content -LiteralPath $script -Encoding ascii -Value @(
            "create vdisk file=`"$vhd`" maximum=16384 type=expandable",
            "select vdisk file=`"$vhd`"",
            'attach vdisk',
            'convert mbr',
            'create partition primary',
            'format fs=ntfs quick label=FJ',
            'assign letter=E'
        )
        diskpart /s $script
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath 'E:\')) { Fail 'diskpart could not create E:' }
    }
    New-Item -ItemType Directory -Force -Path 'E:\tools\probes\package' | Out-Null
}

function PrepareSlint {
    if (Test-Path -LiteralPath $slintDir) {
        $missing = @(SlintMissing)
        if ($missing.Count -gt 0) { Fail "$slintDir exists but has no $($missing -join ', ')" }
        Write-Host "Slint: using $slintDir"
        return
    }
    $url = $env:SLINT_SDK_URL
    if (-not $url) { $url = $defaultSlintUrl }
    $dl = Join-Path $env:RUNNER_TEMP 'slint-dl'
    $tmp = Join-Path $env:RUNNER_TEMP 'slint-x'
    New-Item -ItemType Directory -Force -Path $dl, $tmp | Out-Null
    $file = Join-Path $dl ([System.IO.Path]::GetFileName(([Uri]$url).AbsolutePath))
    Write-Host "Slint: $url"
    Download $url $file
    if ($file -like '*.zip') {
        & $tar -xf $file -C $tmp
    } else {
        $sevenZip = Get-Command 7z.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source
        if (-not $sevenZip -and (Test-Path -LiteralPath 'C:\Program Files\7-Zip\7z.exe')) { $sevenZip = 'C:\Program Files\7-Zip\7z.exe' }
        if (-not $sevenZip) { Fail '7-Zip is needed to unpack the Slint installer (or set FETCHJET_SLINT_SDK_URL to a .zip)' }
        & $sevenZip x -y "-o$tmp" $file | Out-Null
    }
    if ($LASTEXITCODE -ne 0) { Fail 'Slint SDK unpack failed' }
    $cfg = Get-ChildItem -LiteralPath $tmp -Recurse -File | Where-Object { $_.Name -in @('SlintConfig.cmake', 'slint-config.cmake') } | Select-Object -First 1
    if (-not $cfg) { Fail 'no lib\cmake\Slint\SlintConfig.cmake in the Slint SDK' }
    $root = $cfg.Directory.Parent.Parent.Parent.FullName
    New-Item -ItemType Directory -Force -Path 'E:\tools' | Out-Null
    Copy-Item -LiteralPath $root -Destination $slintDir -Recurse
    $libDll = Join-Path $slintDir 'lib\slint_cpp.dll'
    $binDll = Join-Path $slintDir 'bin\slint_cpp.dll'
    if (-not (Test-Path -LiteralPath $libDll) -and (Test-Path -LiteralPath $binDll)) { Copy-Item -LiteralPath $binDll -Destination $libDll }
    $missing = @(SlintMissing)
    if ($missing.Count -gt 0) { Fail "Slint SDK has no $($missing -join ', ')" }
}

function PrepareCMake {
    if (Get-Command cmake.exe -ErrorAction SilentlyContinue) { return }
    $candidates = @('C:\Program Files\CMake\bin')
    $candidates += Get-ChildItem 'C:\Program Files\Microsoft Visual Studio\*\*\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin' -Directory -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | ForEach-Object { $_.FullName }
    foreach ($c in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $c 'cmake.exe')) { AddPath $c; Write-Host "cmake: $c"; return }
    }
    Fail 'cmake.exe not found (install CMake or Visual Studio with C++ CMake tools)'
}

function PrepareWix {
    $wixHome = $env:WIX -and (Test-Path -LiteralPath (Join-Path $env:WIX 'bin\candle.exe'))
    $wixDefault = Test-Path -LiteralPath 'C:\Program Files (x86)\WiX Toolset v3.14\bin\candle.exe'
    $wixPath = $null -ne (Get-Command candle.exe -ErrorAction SilentlyContinue)
    if ($wixHome -or $wixDefault -or $wixPath) { Write-Host 'WiX 3 is installed'; return }
    $nv = (Invoke-RestMethod -Uri 'https://api.nuget.org/v3-flatcontainer/wix/index.json').versions | Where-Object { $_ -match '^3\.14\.\d+$' } | Select-Object -Last 1
    if (-not $nv) { Fail 'WiX 3.14 not found on NuGet' }
    Write-Host "WiX $nv from NuGet"
    $nupkg = Join-Path $env:RUNNER_TEMP 'wix.zip'
    $dir = Join-Path $env:RUNNER_TEMP 'wix'
    Download "https://api.nuget.org/v3-flatcontainer/wix/$nv/wix.$nv.nupkg" $nupkg
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    & $tar -xf $nupkg -C $dir
    if ($LASTEXITCODE -ne 0) { Fail 'WiX unpack failed' }
    $candle = Get-ChildItem -LiteralPath $dir -Recurse -File -Filter 'candle.exe' | Select-Object -First 1
    if (-not $candle -or -not (Test-Path -LiteralPath (Join-Path $candle.DirectoryName 'WixUIExtension.dll'))) { Fail 'candle.exe / WixUIExtension.dll not in the WiX package' }
    AddPath $candle.DirectoryName
}

function PrepareSdk {
    $bin = Get-ChildItem 'C:\Program Files (x86)\Windows Kits\10\bin\10.*\x64\makeappx.exe' -ErrorAction SilentlyContinue |
        Sort-Object { [Version]($_.Directory.Parent.Name) } -Descending | Select-Object -First 1 | ForEach-Object { $_.DirectoryName }
    if (-not $bin -or -not (Test-Path -LiteralPath (Join-Path $bin 'makepri.exe'))) {
        $nv = (Invoke-RestMethod -Uri 'https://api.nuget.org/v3-flatcontainer/microsoft.windows.sdk.buildtools/index.json').versions | Select-Object -Last 1
        Write-Host "Windows SDK build tools $nv from NuGet"
        $nupkg = Join-Path $env:RUNNER_TEMP 'sdkbt.zip'
        $dir = Join-Path $env:RUNNER_TEMP 'sdkbt'
        Download "https://api.nuget.org/v3-flatcontainer/microsoft.windows.sdk.buildtools/$nv/microsoft.windows.sdk.buildtools.$nv.nupkg" $nupkg
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        & $tar -xf $nupkg -C $dir
        if ($LASTEXITCODE -ne 0) { Fail 'Windows SDK build tools unpack failed' }
        $bin = Get-ChildItem -Path (Join-Path $dir 'bin\10.*\x64') -Directory | Sort-Object { [Version]($_.Parent.Name) } -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $bin -or -not (Test-Path -LiteralPath (Join-Path $bin 'makeappx.exe'))) { Fail 'makeappx.exe not found' }
    Write-Host "Windows SDK tools: $bin"
    AddEnv 'FJ_SDK_BIN' $bin
}

function PrepareClang64 {
    $root = 'C:\msys64\clang64'
    $needed = @('bin\clang++.exe', 'bin\cmake.exe', 'bin\ninja.exe', 'lib\libssl.a', 'lib\libcrypto.a', 'include\boost\version.hpp')
    $missing = @($needed | Where-Object { -not (Test-Path -LiteralPath (Join-Path $root $_)) })
    if ($missing.Count -eq 0) { Write-Host "MSYS2 clang64: $root"; return }
    $bash = 'C:\msys64\usr\bin\bash.exe'
    if (-not (Test-Path -LiteralPath $bash)) { Fail "MSYS2 not found at C:\msys64 (the torrent library needs its clang64 toolchain)" }
    Write-Host "MSYS2 clang64 is missing $($missing -join ', '): installing the packages"
    & $bash -lc 'pacman -S --noconfirm --needed mingw-w64-clang-x86_64-clang mingw-w64-clang-x86_64-cmake mingw-w64-clang-x86_64-ninja mingw-w64-clang-x86_64-openssl mingw-w64-clang-x86_64-boost'
    if ($LASTEXITCODE -ne 0) { Fail 'pacman could not install the clang64 packages' }
    $missing = @($needed | Where-Object { -not (Test-Path -LiteralPath (Join-Path $root $_)) })
    if ($missing.Count -gt 0) { Fail "MSYS2 clang64 still has no $($missing -join ', ')" }
}

function PrepareLibtorrent {
    if (Test-Path -LiteralPath (Join-Path $ltDir 'CMakeLists.txt')) {
        Write-Host "libtorrent: using $ltDir"
    } else {
        Write-Host "libtorrent $ltCommit -> $ltDir"
        if (Test-Path -LiteralPath $ltDir) { Remove-Item -LiteralPath $ltDir -Recurse -Force }
        New-Item -ItemType Directory -Force -Path $ltDir | Out-Null
        foreach ($a in @(
                @('init', '-q'),
                @('remote', 'add', 'origin', 'https://github.com/arvidn/libtorrent.git'),
                @('fetch', '-q', '--depth', '1', 'origin', $ltCommit),
                @('checkout', '-q', 'FETCH_HEAD'),
                @('submodule', 'update', '-q', '--init', '--recursive', '--depth', '1'))) {
            & git -C $ltDir @a
            if ($LASTEXITCODE -ne 0) { Fail "git $($a -join ' ') failed" }
        }
    }
    AddEnv 'LIBTORRENT_SOURCE_DIR' ($ltDir -replace '\\', '/')
}

function Package {
    $stage = Join-Path $ciBase 'stage'
    $out = Join-Path $ciBase 'out'
    if (Test-Path -LiteralPath $ciBase) { Remove-Item -LiteralPath $ciBase -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $out | Out-Null

    Ps 'stage.ps1' @('-Stage', $stage, '-RequireFreshCore')
    if (-not (Test-Path -LiteralPath (Join-Path $stage 'fjtorrent.dll'))) { Fail 'fjtorrent.dll is not in the stage folder: torrents would be hidden' }

    $env:FJ_STAGE = $stage
    $env:FJ_OUT = $out
    & (Join-Path $packaging 'build-msi.cmd')
    if ($LASTEXITCODE -ne 0) {
        $log = Join-Path $out 'build-msi.log'
        if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 150 }
        Fail 'build-msi.cmd failed'
    }

    $version = "$(& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $packaging 'version.ps1'))".Trim()
    if ($LASTEXITCODE -ne 0 -or -not $version) { Fail 'could not read APP_VERSION' }
    $msi = Join-Path $out "FetchJet-$version.msi"
    if (-not (Test-Path -LiteralPath $msi)) { Fail "no $msi" }
    Ps 'check-msi.ps1' @('-Msi', $msi, '-Stage', $stage, '-Scratch', (Join-Path $ciBase 'check-msi'))

    Ps 'msix\build-msix.ps1' @('-Stage', $stage, '-Out', $out, '-SdkBin', $env:FJ_SDK_BIN)
    $msix = Join-Path $out "FetchJet-$version-store.msix"
    if (-not (Test-Path -LiteralPath $msix)) { Fail "no $msix" }
    Ps 'msix\check-msix.ps1' @('-Msix', $msix, '-Stage', $stage, '-Scratch', (Join-Path $ciBase 'check-msix'), '-SdkBin', $env:FJ_SDK_BIN)

    Compress-Archive -Path (Join-Path $stage '*') -DestinationPath (Join-Path $out "FetchJet-$version-native-stage.zip")
    Get-ChildItem -LiteralPath $out -File | Where-Object { $_.Extension -in @('.msi', '.msix', '.zip') } |
        ForEach-Object { Write-Host ("{0}  {1:N0} bytes" -f $_.FullName, $_.Length) }
}

if ($Step -eq 'prepare') {
    PrepareDriveE
    PrepareSlint
    PrepareCMake
    PrepareWix
    PrepareSdk
    PrepareClang64
    PrepareLibtorrent
} else {
    Package
}
exit 0
