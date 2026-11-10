param(
    [string]$Msix = '',
    [string]$Stage = 'E:\tools\probes\package\stage',
    [string]$Scratch = 'E:\tools\probes\package\check-msix',
    [string]$SdkBin = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
$packaging = Split-Path -Parent $PSScriptRoot
. (Join-Path $packaging 'compare-tree.ps1')

$failures = New-Object System.Collections.Generic.List[string]
function Check([bool]$ok, [string]$text) {
    if ($ok) { Write-Host "  ok    $text" } else { Write-Host "  FAIL  $text"; $failures.Add($text) }
}

$scratchFull = [System.IO.Path]::GetFullPath($Scratch).TrimEnd('\')
if (-not $scratchFull.StartsWith('E:\tools\probes\package\', [System.StringComparison]::OrdinalIgnoreCase)) {
    Write-Host "check-msix: FAILED: scratch folder must be under E:\tools\probes\package\: $scratchFull"
    exit 1
}
if (-not $Msix) {
    $Msix = Get-ChildItem 'E:\tools\probes\package\out\FetchJet-*.msix' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $Msix -or -not (Test-Path -LiteralPath $Msix)) { Write-Host 'check-msix: FAILED: no MSIX (pass -Msix)'; exit 1 }
$Msix = (Resolve-Path -LiteralPath $Msix).Path
if (-not $SdkBin) {
    $SdkBin = Get-ChildItem 'C:\Program Files (x86)\Windows Kits\10\bin\10.*\x64\makeappx.exe' -ErrorAction SilentlyContinue |
        Sort-Object { [Version]($_.Directory.Parent.Name) } -Descending | Select-Object -First 1 | ForEach-Object { $_.DirectoryName }
}
if (-not $SdkBin) { Write-Host 'check-msix: FAILED: makeappx.exe not found'; exit 1 }
$version = (& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $packaging 'version.ps1') -Four).Trim()

if (Test-Path -LiteralPath $scratchFull) { Remove-Item -LiteralPath $scratchFull -Recurse -Force }
[void](New-Item -ItemType Directory -Path $scratchFull)
$unpacked = Join-Path $scratchFull 'unpacked'
$unpackLog = Join-Path $scratchFull 'unpack.log'

Write-Host "check-msix: $Msix ($((Get-Item -LiteralPath $Msix).Length) bytes)"
Write-Host 'Unpack (makeappx unpack, not an install)'
$p = Start-Process -FilePath (Join-Path $SdkBin 'makeappx.exe') -ArgumentList @('unpack', '/o', '/p', "`"$Msix`"", '/d', "`"$unpacked`"") -NoNewWindow -Wait -PassThru -RedirectStandardOutput $unpackLog
Check ($p.ExitCode -eq 0) "makeappx unpack exit $($p.ExitCode) (log $unpackLog)"
if ($p.ExitCode -ne 0) { exit 1 }

Write-Host 'Manifest'
[xml]$m = Get-Content -LiteralPath (Join-Path $unpacked 'AppxManifest.xml') -Raw
$ns = New-Object System.Xml.XmlNamespaceManager($m.NameTable)
$ns.AddNamespace('f', 'http://schemas.microsoft.com/appx/manifest/foundation/windows10')
$ns.AddNamespace('uap', 'http://schemas.microsoft.com/appx/manifest/uap/windows10')
$ns.AddNamespace('uap3', 'http://schemas.microsoft.com/appx/manifest/uap/windows10/3')
$ns.AddNamespace('desktop', 'http://schemas.microsoft.com/appx/manifest/desktop/windows10')
$ns.AddNamespace('rescap', 'http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities')
function One([string]$xpath) { return $m.SelectSingleNode($xpath, $ns) }

$id = One '/f:Package/f:Identity'
Check ($id.Name -eq 'MichelleRaouf.FetchJet') "Identity Name = $($id.Name)"
Check ($id.Publisher -eq 'CN=81BF98FB-E3BB-479B-BCA6-3F56A36736A0') "Identity Publisher = $($id.Publisher)"
Check ($id.Version -eq $version) "Identity Version = $($id.Version) (APP_VERSION $version)"
Check ($id.ProcessorArchitecture -eq 'x64') "ProcessorArchitecture = $($id.ProcessorArchitecture)"
$jvm = Join-Path $packaging '..\..\desktopApp\msix\AppxManifest.xml'
if (Test-Path -LiteralPath $jvm) {
    [xml]$j = Get-Content -LiteralPath $jvm -Raw
    Check ($j.Package.Identity.Name -eq $id.Name -and $j.Package.Identity.Publisher -eq $id.Publisher) 'Identity equals the JVM Store manifest (desktopApp\msix)'
}
$app = One '/f:Package/f:Applications/f:Application'
Check ($app.Executable -eq 'FetchJet.exe' -and $app.EntryPoint -eq 'Windows.FullTrustApplication') "Application $($app.Id): $($app.Executable), $($app.EntryPoint)"
Check (Test-Path -LiteralPath (Join-Path $unpacked $app.Executable)) 'Executable is in the package root'
$st = One '//desktop:StartupTask'
Check ($st -and $st.TaskId -eq 'FetchJetStartup' -and $st.Enabled -eq 'true') "StartupTask $(if ($st) { $st.TaskId + ' Enabled=' + $st.Enabled })"
Check ($null -ne (One "//rescap:Capability[@Name='runFullTrust']")) 'rescap:runFullTrust'
Check ($null -ne (One "//uap:Protocol[@Name='fetchjet']") -and $null -ne (One "//uap:Protocol[@Name='magnet']")) 'Protocols fetchjet and magnet'
Check ($null -ne (One "//uap:FileType[.='.torrent']")) 'File type .torrent'
Check ($null -ne (One "//desktop:ExecutionAlias[@Alias='fetchjet.exe']")) 'Execution alias fetchjet.exe'
$logos = @((One '/f:Package/f:Properties/f:Logo').InnerText)
$ve = One '//uap:VisualElements'
$logos += $ve.Square150x150Logo, $ve.Square44x44Logo, (One '//uap:DefaultTile').Wide310x150Logo
foreach ($l in $logos) {
    $base = [System.IO.Path]::GetFileNameWithoutExtension($l)
    $dir = Join-Path $unpacked (Split-Path -Parent $l)
    $found = @(Get-ChildItem -LiteralPath $dir -File -Filter "$base*" -ErrorAction SilentlyContinue)
    Check ($found.Count -gt 0) "Logo $l ($($found.Count) files)"
}
Check (Test-Path -LiteralPath (Join-Path $unpacked 'resources.pri')) 'resources.pri present'
Check (Test-Path -LiteralPath (Join-Path $unpacked 'AppxBlockMap.xml')) 'AppxBlockMap.xml present'
$signed = Test-Path -LiteralPath (Join-Path $unpacked 'AppxSignature.p7x')
Write-Host "        signed: $signed (a Store upload must be unsigned)"

Write-Host 'Tree compare with the stage (FetchJet.installed is left out of the MSIX)'
$assetSrc = Join-Path $PSScriptRoot 'Assets'
$assetRes = Compare-Trees $assetSrc (Join-Path $unpacked 'Assets') @() @()
foreach ($pr in $assetRes.Problems) { Write-Host "        Assets: $pr" }
Check ($assetRes.Problems.Count -eq 0) "Assets: $($assetRes.ExpectedCount) files, same SHA-256"
$priFiles = @(Get-ChildItem -LiteralPath $unpacked -File -Filter 'resources*.pri' | ForEach-Object { $_.Name })
Write-Host "        makepri files: $($priFiles -join ', ')"
$skipActual = @('AppxManifest.xml', 'AppxBlockMap.xml', '[Content_Types].xml', 'AppxSignature.p7x') + $priFiles +
    @(Get-ChildItem -LiteralPath (Join-Path $unpacked 'Assets') -File | ForEach-Object { 'Assets\' + $_.Name })
$res = Compare-Trees $Stage $unpacked @('FetchJet.installed') $skipActual
foreach ($pr in $res.Problems) { Write-Host "        $pr" }
Check ($res.Problems.Count -eq 0) "Stage $($res.ExpectedCount) files, package $($res.ActualCount) files, same SHA-256"

if ($failures.Count -gt 0) { Write-Host "check-msix: $($failures.Count) FAILED"; exit 1 }
Write-Host 'check-msix: all checks passed'
exit 0
