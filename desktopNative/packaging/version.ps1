param([switch]$Four)

$ErrorActionPreference = 'Stop'
$file = Join-Path $PSScriptRoot '..\..\sharedLogic\src\commonMain\kotlin\com\michelle\raouf\fetch_jet\core\update\AppVersion.kt'
$m = [regex]::Match((Get-Content -LiteralPath $file -Raw), 'const\s+val\s+APP_VERSION\s*=\s*"([^"]+)"')
if (-not $m.Success) { [Console]::Error.WriteLine("version: APP_VERSION not found in $file"); exit 1 }
$v = $m.Groups[1].Value.Split('-')[0]
$parts = @($v.Split('.'))
foreach ($p in $parts) { if ($p -notmatch '^\d+$') { [Console]::Error.WriteLine("version: not a number version: $v"); exit 1 } }
while ($parts.Count -lt 3) { $parts += '0' }
if ($parts.Count -gt 3) { $parts = $parts[0..2] }
if ($Four) { $parts += '0' }
Write-Output ($parts -join '.')
exit 0
