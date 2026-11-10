param(
    [Parameter(Mandatory = $true)][string]$Stage,
    [Parameter(Mandatory = $true)][string]$Out
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$stageFull = [System.IO.Path]::GetFullPath($Stage).TrimEnd('\')
if (-not (Test-Path -LiteralPath $stageFull -PathType Container)) { Write-Host "gen-files: FAILED: no stage folder $stageFull"; exit 1 }

$namespace = [Guid]'7E1C3F2A-5B7D-4C8E-9A61-4F2A9C1B7D30'
$sha1 = [System.Security.Cryptography.SHA1]::Create()
$md5 = [System.Security.Cryptography.MD5]::Create()

function Swap([byte[]]$b) {
    [Array]::Reverse($b, 0, 4)
    [Array]::Reverse($b, 4, 2)
    [Array]::Reverse($b, 6, 2)
}

function NameGuid([string]$name) {
    $ns = $namespace.ToByteArray()
    Swap $ns
    $data = $ns + [System.Text.Encoding]::UTF8.GetBytes($name.ToLowerInvariant())
    $hash = $sha1.ComputeHash([byte[]]$data)
    $g = New-Object byte[] 16
    [Array]::Copy($hash, $g, 16)
    $g[6] = ($g[6] -band 0x0F) -bor 0x50
    $g[8] = ($g[8] -band 0x3F) -bor 0x80
    Swap $g
    return ([Guid]::new($g)).ToString('B').ToUpperInvariant()
}

function NameId([string]$prefix, [string]$name) {
    $hash = $md5.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($name.ToLowerInvariant()))
    return $prefix + (($hash | ForEach-Object { $_.ToString('x2') }) -join '')
}

function Esc([string]$s) { return [System.Security.SecurityElement]::Escape($s) }

$dirs = @{}
$dirs[''] = 'INSTALLDIR'
Get-ChildItem -LiteralPath $stageFull -Recurse -Directory | ForEach-Object {
    $rel = $_.FullName.Substring($stageFull.Length + 1)
    $dirs[$rel] = NameId 'd_' $rel
}
$files = Get-ChildItem -LiteralPath $stageFull -Recurse -File | Sort-Object FullName

$sb = New-Object System.Text.StringBuilder
function W([string]$line) { [void]$sb.AppendLine($line) }

function WriteDir([string]$rel, [string]$indent) {
    $children = $dirs.Keys | Where-Object { $_ -ne '' -and (Split-Path -Parent $_) -eq $rel } | Sort-Object
    foreach ($c in $children) {
        $name = Split-Path -Leaf $c
        W "$indent<Directory Id=`"$($dirs[$c])`" Name=`"$(Esc $name)`">"
        WriteDir $c ($indent + '  ')
        W "$indent</Directory>"
    }
}

$keyPath = 'Key="Software\Michelle Raouf\FetchJet" Name="ProductCode" Value="[ProductCode]" Type="string" KeyPath="yes"'

W '<?xml version="1.0" encoding="utf-8"?>'
W '<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi">'
W '  <Fragment>'
W '    <DirectoryRef Id="INSTALLDIR">'
WriteDir '' '      '
W '    </DirectoryRef>'
W '  </Fragment>'
W '  <Fragment>'
W '    <ComponentGroup Id="AppFiles">'
foreach ($rel in ($dirs.Keys | Sort-Object)) {
    $dirId = $dirs[$rel]
    $key = "dir:" + $rel
    W "      <Component Id=`"$(NameId 'cd_' $key)`" Directory=`"$dirId`" Guid=`"$(NameGuid $key)`">"
    W "        <RemoveFolder Id=`"$(NameId 'rf_' $key)`" On=`"uninstall`" />"
    W "        <RegistryValue Id=`"$(NameId 'rd_' $key)`" Root=`"HKCU`" $keyPath />"
    W '      </Component>'
}
foreach ($f in $files) {
    $rel = $f.FullName.Substring($stageFull.Length + 1)
    $parent = Split-Path -Parent $rel
    $dirId = $dirs[$parent]
    $key = "file:" + $rel
    W "      <Component Id=`"$(NameId 'cf_' $key)`" Directory=`"$dirId`" Guid=`"$(NameGuid $key)`">"
    W "        <File Id=`"$(NameId 'f_' $key)`" Name=`"$(Esc $f.Name)`" Source=`"`$(var.Stage)\$(Esc $rel)`" KeyPath=`"no`" />"
    W "        <RegistryValue Id=`"$(NameId 'rk_' $key)`" Root=`"HKCU`" $keyPath />"
    W '      </Component>'
}
W '    </ComponentGroup>'
W '  </Fragment>'
W '</Wix>'

$outFull = [System.IO.Path]::GetFullPath($Out)
$outDir = Split-Path -Parent $outFull
if (-not (Test-Path -LiteralPath $outDir)) { [void](New-Item -ItemType Directory -Path $outDir -Force) }
[System.IO.File]::WriteAllText($outFull, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("gen-files: {0} files, {1} folders -> {2}" -f $files.Count, $dirs.Count, $outFull)
exit 0
