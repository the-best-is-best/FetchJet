param(
    [string]$Stage = 'E:\tools\probes\package\stage',
    [string]$Out = 'E:\tools\probes\package\out',
    [string]$SdkBin = '',
    [string]$IdentityName = '',
    [string]$Publisher = '',
    [string]$CertPfx = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$here = $PSScriptRoot
$packaging = Split-Path -Parent $here
$log = Join-Path $Out 'build-msix.log'

function Fail([string]$text) {
    Write-Host "build-msix: FAILED: $text"
    if (Test-Path -LiteralPath (Split-Path -Parent $log)) { Add-Content -LiteralPath $log -Value "FAILED: $text`r`nEXIT 1" }
    exit 1
}

function Run([string]$exe, [string[]]$arguments, [string]$name) {
    $o = Join-Path $Out "$name.out.txt"
    $e = Join-Path $Out "$name.err.txt"
    $quoted = $arguments | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }
    $shown = $quoted -join ' '
    if ($env:FJ_MSIX_CERT_PASSWORD) { $shown = $shown.Replace($env:FJ_MSIX_CERT_PASSWORD, '***') }
    Add-Content -LiteralPath $log -Value ("RUN {0} {1}" -f $exe, $shown)
    $p = Start-Process -FilePath $exe -ArgumentList ($quoted -join ' ') -NoNewWindow -PassThru -RedirectStandardOutput $o -RedirectStandardError $e
    $null = $p.Handle
    try { $p.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::Idle } catch { }
    $p.WaitForExit()
    $code = $p.ExitCode
    Get-Content -LiteralPath $o, $e -ErrorAction SilentlyContinue | Add-Content -LiteralPath $log
    Add-Content -LiteralPath $log -Value "EXIT $code ($name)"
    return $code
}

if (-not (Test-Path -LiteralPath (Join-Path $Stage 'FetchJet.exe'))) { Fail "no stage in $Stage, run packaging\stage.ps1 first" }
if (-not (Test-Path -LiteralPath $Out)) { [void](New-Item -ItemType Directory -Path $Out -Force) }
Set-Content -LiteralPath $log -Value ("START {0}" -f (Get-Date -Format s))

if (-not $SdkBin) {
    $SdkBin = Get-ChildItem 'C:\Program Files (x86)\Windows Kits\10\bin\10.*\x64\makeappx.exe' -ErrorAction SilentlyContinue |
        Sort-Object { [Version]($_.Directory.Parent.Name) } -Descending | Select-Object -First 1 | ForEach-Object { $_.DirectoryName }
}
if (-not $SdkBin) { Fail 'makeappx.exe not found under C:\Program Files (x86)\Windows Kits\10\bin\10.*\x64 (pass -SdkBin)' }
$makeappx = Join-Path $SdkBin 'makeappx.exe'
$makepri = Join-Path $SdkBin 'makepri.exe'
$signtool = Join-Path $SdkBin 'signtool.exe'
foreach ($t in @($makeappx, $makepri)) { if (-not (Test-Path -LiteralPath $t)) { Fail "missing $t" } }

$version = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $packaging 'version.ps1') -Four
if ($LASTEXITCODE -ne 0 -or -not $version) { Fail 'could not read APP_VERSION' }
$version = "$version".Trim()
$shortVersion = $version.Substring(0, $version.LastIndexOf('.'))

$pkgStage = Join-Path $Out 'msix-stage'
if (Test-Path -LiteralPath $pkgStage) { Remove-Item -LiteralPath $pkgStage -Recurse -Force }
[void](New-Item -ItemType Directory -Path $pkgStage)
Copy-Item -Path (Join-Path $Stage '*') -Destination $pkgStage -Recurse -Force
Remove-Item -LiteralPath (Join-Path $pkgStage 'FetchJet.installed') -Force -ErrorAction SilentlyContinue
Copy-Item -LiteralPath (Join-Path $here 'Assets') -Destination (Join-Path $pkgStage 'Assets') -Recurse -Force

$manifestText = Get-Content -LiteralPath (Join-Path $here 'AppxManifest.xml') -Raw
$manifestText = $manifestText.Replace('__VERSION__', $version)
if ($IdentityName) { $manifestText = $manifestText.Replace('Name="MichelleRaouf.FetchJet"', "Name=`"$IdentityName`"") }
if ($Publisher) { $manifestText = $manifestText.Replace('Publisher="CN=81BF98FB-E3BB-479B-BCA6-3F56A36736A0"', "Publisher=`"$([System.Security.SecurityElement]::Escape($Publisher))`"") }
[xml]$check = $manifestText
$manifestPath = Join-Path $pkgStage 'AppxManifest.xml'
[System.IO.File]::WriteAllText($manifestPath, $manifestText, (New-Object System.Text.UTF8Encoding($false)))

$priConfig = Join-Path $Out 'priconfig.xml'
$code = Run $makepri @('createconfig', '/cf', $priConfig, '/dq', 'en-US', '/o') 'makepri-config'
if ($code -eq 0) {
    $code = Run $makepri @('new', '/pr', $pkgStage, '/cf', $priConfig, '/mn', $manifestPath, '/of', (Join-Path $pkgStage 'resources.pri'), '/o') 'makepri-new'
}
if ($code -ne 0) { Write-Host "build-msix: WARNING: makepri failed ($code), packing without resources.pri (CI does the same)" }

$suffix = if ($IdentityName -or $Publisher) { '-test' } else { '-store' }
$msix = Join-Path $Out "FetchJet-$shortVersion$suffix.msix"
if (Test-Path -LiteralPath $msix) { Remove-Item -LiteralPath $msix -Force }
$code = Run $makeappx @('pack', '/o', '/d', $pkgStage, '/p', $msix) 'makeappx-pack'
if ($code -ne 0 -or -not (Test-Path -LiteralPath $msix)) { Fail "makeappx pack failed ($code), see $log" }

if ($CertPfx) {
    if (-not (Test-Path -LiteralPath $signtool)) { Fail "missing $signtool" }
    if (-not (Test-Path -LiteralPath $CertPfx)) { Fail "missing $CertPfx" }
    $password = $env:FJ_MSIX_CERT_PASSWORD
    $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2
    $cert.Import($CertPfx, $password, [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet)
    $wanted = $check.Package.Identity.Publisher
    if ($cert.Subject -ne $wanted) { Fail "certificate subject '$($cert.Subject)' != manifest Publisher '$wanted' (use -Publisher)" }
    $signArgs = @('sign', '/fd', 'SHA256', '/f', $CertPfx)
    if ($password) { $signArgs += @('/p', $password) }
    $signArgs += $msix
    $code = Run $signtool $signArgs 'signtool'
    if ($code -ne 0) { Fail "signtool failed ($code)" }
    Write-Host "build-msix: signed with $CertPfx (nothing was imported into a certificate store)"
} else {
    Write-Host 'build-msix: not signed (the Store signs the upload)'
}

Add-Content -LiteralPath $log -Value "EXIT 0"
$size = (Get-Item -LiteralPath $msix).Length
Write-Host ("build-msix: ok, {0} ({1:N0} bytes), identity {2} {3}, publisher {4}" -f $msix, $size, $check.Package.Identity.Name, $version, $check.Package.Identity.Publisher)
exit 0
