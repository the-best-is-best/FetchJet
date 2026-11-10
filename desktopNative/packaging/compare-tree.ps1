function Get-TreeMap([string]$root, [string[]]$skip) {
    $rootFull = [System.IO.Path]::GetFullPath($root).TrimEnd('\')
    $map = @{}
    Get-ChildItem -LiteralPath $rootFull -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($rootFull.Length + 1)
        if ($skip -notcontains $rel) {
            $map[$rel.ToLowerInvariant()] = [pscustomobject]@{
                Path   = $rel
                Length = $_.Length
                Hash   = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
            }
        }
    }
    return $map
}

function Compare-Trees([string]$expected, [string]$actual, [string[]]$skipExpected, [string[]]$skipActual) {
    $a = Get-TreeMap $expected $skipExpected
    $b = Get-TreeMap $actual $skipActual
    $problems = New-Object System.Collections.Generic.List[string]
    foreach ($k in ($a.Keys | Sort-Object)) {
        if (-not $b.ContainsKey($k)) { $problems.Add("missing in package: $($a[$k].Path)"); continue }
        if ($a[$k].Hash -ne $b[$k].Hash) { $problems.Add("different content: $($a[$k].Path) ($($a[$k].Length) vs $($b[$k].Length) bytes)") }
    }
    foreach ($k in ($b.Keys | Sort-Object)) {
        if (-not $a.ContainsKey($k)) { $problems.Add("extra in package: $($b[$k].Path)") }
    }
    $emptyA = @(Get-ChildItem -LiteralPath $expected -Recurse -Directory | Where-Object { -not (Get-ChildItem -LiteralPath $_.FullName -Recurse -File) }).Count
    return [pscustomobject]@{
        ExpectedCount = $a.Count
        ActualCount   = $b.Count
        Problems      = $problems
        EmptyFolders  = $emptyA
    }
}
