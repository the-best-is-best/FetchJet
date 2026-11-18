param(
    [Parameter(Mandatory = $true)][string]$StageZip,
    [Parameter(Mandatory = $true)][string]$Out
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

if (-not (Test-Path -LiteralPath $StageZip -PathType Leaf)) { Write-Host "portable: FAILED: missing $StageZip"; exit 1 }
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("fetchjet-portable-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $work | Out-Null
try {
    Expand-Archive -LiteralPath $StageZip -DestinationPath $work -Force
    $exe = Get-ChildItem -LiteralPath $work -Recurse -Filter 'FetchJet.exe' | Select-Object -First 1
    if (-not $exe) { Write-Host "portable: FAILED: no FetchJet.exe in $StageZip"; exit 1 }
    $root = $exe.Directory.FullName
    Remove-Item -LiteralPath (Join-Path $root 'FetchJet.installed') -Force -ErrorAction SilentlyContinue
    Set-Content -LiteralPath (Join-Path $root 'FetchJet.portable') -Value 'FetchJet keeps its settings and downloads list in the data folder next to this file.' -Encoding utf8
    if (Test-Path -LiteralPath $Out) { Remove-Item -LiteralPath $Out -Force }
    Compress-Archive -Path (Join-Path $root '*') -DestinationPath $Out -CompressionLevel Optimal
    Write-Host "portable: wrote $Out"
} finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}
