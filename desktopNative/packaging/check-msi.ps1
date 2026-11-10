param(
    [string]$Msi = '',
    [string]$Stage = 'E:\tools\probes\package\stage',
    [string]$Scratch = 'E:\tools\probes\package\check-msi'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
. (Join-Path $PSScriptRoot 'compare-tree.ps1')

$failures = New-Object System.Collections.Generic.List[string]
function Check([bool]$ok, [string]$text) {
    if ($ok) { Write-Host "  ok    $text" } else { Write-Host "  FAIL  $text"; $failures.Add($text) }
}

$scratchFull = [System.IO.Path]::GetFullPath($Scratch).TrimEnd('\')
if (-not $scratchFull.StartsWith('E:\tools\probes\package\', [System.StringComparison]::OrdinalIgnoreCase)) {
    Write-Host "check-msi: FAILED: scratch folder must be under E:\tools\probes\package\: $scratchFull"
    exit 1
}
if (-not $Msi) {
    $Msi = Get-ChildItem 'E:\tools\probes\package\out\FetchJet-*.msi' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $Msi -or -not (Test-Path -LiteralPath $Msi)) { Write-Host 'check-msi: FAILED: no MSI (pass -Msi)'; exit 1 }
$Msi = (Resolve-Path -LiteralPath $Msi).Path
$version = (& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'version.ps1')).Trim()

if (Test-Path -LiteralPath $scratchFull) { Remove-Item -LiteralPath $scratchFull -Recurse -Force }
[void](New-Item -ItemType Directory -Path $scratchFull)
$tablesFile = Join-Path $scratchFull 'tables.txt'

function Invoke([object]$o, [string]$name, [string]$kind, [object[]]$a) {
    $plain = [object[]]@($a | ForEach-Object { if ($_ -is [System.Management.Automation.PSObject]) { $_.PSObject.BaseObject } else { $_ } })
    return $o.GetType().InvokeMember($name, $kind, $null, $o, $plain)
}

$installer = New-Object -ComObject WindowsInstaller.Installer
$db = Invoke $installer 'OpenDatabase' 'InvokeMethod' @($Msi, 0)

function Query([string]$sql) {
    $view = Invoke $db 'OpenView' 'InvokeMethod' @($sql)
    [void](Invoke $view 'Execute' 'InvokeMethod' @())
    $names = Invoke $view 'ColumnInfo' 'GetProperty' @(0)
    $count = Invoke $names 'FieldCount' 'GetProperty' @()
    $cols = @(1..$count | ForEach-Object { Invoke $names 'StringData' 'GetProperty' @($_) })
    $rows = New-Object System.Collections.Generic.List[object]
    while ($true) {
        $rec = Invoke $view 'Fetch' 'InvokeMethod' @()
        if ($null -eq $rec) { break }
        $row = [ordered]@{}
        for ($i = 1; $i -le $count; $i++) {
            try { $row[$cols[$i - 1]] = Invoke $rec 'StringData' 'GetProperty' @($i) } catch { $row[$cols[$i - 1]] = '<stream>' }
        }
        $rows.Add([pscustomobject]$row)
    }
    [void](Invoke $view 'Close' 'InvokeMethod' @())
    return $rows
}

$tableNames = Query 'SELECT `Name` FROM `_Tables`' | ForEach-Object { $_.Name } | Sort-Object
$dump = New-Object System.Text.StringBuilder
$tables = @{}
foreach ($t in $tableNames) {
    $sql = 'SELECT * FROM `' + $t + '`'
    if (@('Binary', 'Icon', 'MsiDigitalSignature', 'MsiPatchCertificate') -contains $t) {
        $n = @(Query $sql).Count
        [void]$dump.AppendLine("== $t (stream table, $n rows)")
        continue
    }
    $rows = @(Query $sql)
    $tables[$t] = $rows
    [void]$dump.AppendLine("== $t ($($rows.Count) rows)")
    foreach ($r in $rows) { [void]$dump.AppendLine((($r.PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ' | ')) }
}
$summary = Invoke $installer 'SummaryInformation' 'GetProperty' @($Msi, 0)
$wordCount = Invoke $summary 'Property' 'GetProperty' @(15)
$template = Invoke $summary 'Property' 'GetProperty' @(7)
[void]$dump.AppendLine("== SummaryInformation: Template=$template WordCount=$wordCount")
[System.IO.File]::WriteAllText($tablesFile, $dump.ToString(), (New-Object System.Text.UTF8Encoding($false)))
[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($summary)
[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($db)
[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($installer)
$db = $null; $installer = $null
[GC]::Collect(); [GC]::WaitForPendingFinalizers()

function Prop([string]$name) { $r = $tables['Property'] | Where-Object { $_.Property -eq $name }; if ($r) { return $r.Value } return $null }

Write-Host "check-msi: $Msi ($((Get-Item -LiteralPath $Msi).Length) bytes)"
Write-Host 'Tables'
Check ((Prop 'ProductName') -eq 'FetchJet') "ProductName = $(Prop 'ProductName')"
Check ((Prop 'Manufacturer') -eq 'Michelle Raouf') "Manufacturer = $(Prop 'Manufacturer')"
Check ((Prop 'ProductVersion') -eq $version) "ProductVersion = $(Prop 'ProductVersion') (APP_VERSION $version)"
Check ((Prop 'UpgradeCode') -eq '{7E1C3F2A-5B7D-4C8E-9A61-4F2A9C1B7D30}') "UpgradeCode = $(Prop 'UpgradeCode')"
Check ($null -eq (Prop 'ALLUSERS')) "ALLUSERS not set (per user) = '$(Prop 'ALLUSERS')'"
Check ((([int]$wordCount) -band 8) -eq 8) "Summary WordCount $wordCount has bit 8 (no elevation)"
Check ($template -eq 'x64;1033') "Summary Template = $template"
$up = @($tables['Upgrade'])
Check ($up.Count -ge 1) "Upgrade rows: $($up.Count)"
foreach ($u in $up) { Write-Host "        Upgrade $($u.UpgradeCode) min='$($u.VersionMin)' max='$($u.VersionMax)' attr=$($u.Attributes) -> $($u.ActionProperty)" }
$seq = @($tables['InstallExecuteSequence'])
$rep = $seq | Where-Object { $_.Action -eq 'RemoveExistingProducts' }
$iv = $seq | Where-Object { $_.Action -eq 'InstallValidate' }
Check ($rep -and $iv -and [int]$rep.Sequence -gt [int]$iv.Sequence -and [int]$rep.Sequence -lt 1500) "RemoveExistingProducts at $(if ($rep) { $rep.Sequence }) (InstallValidate $(if ($iv) { $iv.Sequence }))"
$dirs = @($tables['Directory'])
$inst = $dirs | Where-Object { $_.Directory -eq 'INSTALLDIR' }
Check ($inst -and $inst.Directory_Parent -eq 'LocalAppDataFolder' -and $inst.DefaultDir -match 'FetchJet') "INSTALLDIR = LocalAppDataFolder\$(if ($inst) { $inst.DefaultDir })"
$sc = @($tables['Shortcut'])
Check ($sc.Count -eq 2) "Shortcuts: $($sc.Count) ($((($sc | ForEach-Object { $_.Directory_ + ':' + $_.Name }) -join ', ')))"
foreach ($s in $sc) { Check ($s.Target -eq '[INSTALLDIR]FetchJet.exe' -and $s.WkDir -eq 'INSTALLDIR') "Shortcut $($s.Shortcut) target $($s.Target) workdir $($s.WkDir)" }
$reg = @($tables['Registry'])
$progid = 'progidb6b76d118e2c3f3e9f64d373ac4670d0'
$cmd = $reg | Where-Object { $_.Key -eq "Software\Classes\$progid\shell\open\command" }
Check ($cmd -and $cmd.Value -eq '"[INSTALLDIR]FetchJet.exe" "%1" %*' -and $cmd.Root -eq '1') "torrent command = $(if ($cmd) { $cmd.Value })"
$ext = $reg | Where-Object { $_.Key -eq 'Software\Classes\.torrent' -and -not $_.Name }
Check ($ext -and $ext.Value -eq $progid) ".torrent default = $(if ($ext) { $ext.Value })"
$compCond = @($tables['Component'] | Where-Object { $_.Condition })
Check ($compCond.Count -eq 2) "Conditional components: $((($compCond | ForEach-Object { $_.Component + '=' + $_.Condition }) -join ', '))"
$fileRows = @($tables['File'])
$stageFiles = @(Get-ChildItem -LiteralPath $Stage -Recurse -File)
Check ($fileRows.Count -eq $stageFiles.Count) "File rows $($fileRows.Count), stage files $($stageFiles.Count)"
$comps = @($tables['Component'])
$badKey = @($comps | Where-Object { -not $_.KeyPath -or ($_.Attributes -band 4) -eq 0 })
Check ($badKey.Count -eq 0) "Every component has a registry key path ($($comps.Count) components)"
$rf = @($tables['RemoveFile'] | Where-Object { -not $_.FileName })
$dirsUnderInstall = @($dirs | Where-Object { $_.Directory -like 'd_*' -or $_.Directory -eq 'INSTALLDIR' })
Check ($rf.Count -ge $dirsUnderInstall.Count + 1) "RemoveFolder rows $($rf.Count) for $($dirsUnderInstall.Count) install folders + ProgramMenuDir"
$ui = $tables.ContainsKey('Dialog') -and @($tables['Dialog'] | Where-Object { $_.Dialog -eq 'InstallDirDlg' }).Count -eq 1
Check $ui 'WixUI_InstallDir dialogs present'
$pub = @($tables['ControlEvent'] | Where-Object { $_.Dialog_ -eq 'WelcomeDlg' -and $_.Control_ -eq 'Next' -and $_.Argument -eq 'InstallDirDlg' })
Check ($pub.Count -ge 1) 'WelcomeDlg Next -> InstallDirDlg (licence page skipped)'

Write-Host 'Administrative extraction (msiexec /a, not an install)'
$admin = Join-Path $scratchFull 'admin'
$adminLog = Join-Path $scratchFull 'admin.log'
[void](New-Item -ItemType Directory -Path $admin)
$p = Start-Process -FilePath "$env:SystemRoot\System32\msiexec.exe" -ArgumentList @('/a', "`"$Msi`"", '/qn', "TARGETDIR=`"$admin`"", '/l*v', "`"$adminLog`"") -Wait -PassThru
Check ($p.ExitCode -eq 0) "msiexec /a exit $($p.ExitCode) (log $adminLog)"
$exe = Get-ChildItem -LiteralPath $admin -Recurse -File -Filter 'FetchJet.exe' | Select-Object -First 1
Check ($null -ne $exe) "FetchJet.exe found in the extracted tree$(if ($exe) { ': ' + $exe.DirectoryName.Substring($admin.Length) })"
if ($exe) {
    $result = Compare-Trees $Stage $exe.DirectoryName @() @()
    foreach ($pr in $result.Problems) { Write-Host "        $pr" }
    Check ($result.Problems.Count -eq 0) "Tree compare: stage $($result.ExpectedCount) files, extracted $($result.ActualCount) files, same SHA-256"
}

Write-Host "check-msi: table dump $tablesFile"
if ($failures.Count -gt 0) { Write-Host "check-msi: $($failures.Count) FAILED"; exit 1 }
Write-Host 'check-msi: all checks passed'
exit 0
