# Packages the extension for Chrome/Edge (zip) and Firefox (xpi) on Windows.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$version = (Get-Content manifest.json -Raw | ConvertFrom-Json).version
$out = Join-Path $PSScriptRoot "..\build\extension"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path "$out\chrome", "$out\firefox" | Out-Null
$files = "background.js","i18n","_locales","yt-core.js","content.js","popup.html","popup.js","options.html","options.js","icon16.png","icon48.png","icon128.png"
Copy-Item ($files + "manifest.json" + "platform.js") "$out\chrome\" -Recurse
Copy-Item $files "$out\firefox\" -Recurse
Copy-Item "manifest.firefox.json" "$out\firefox\manifest.json"
Copy-Item "platform.firefox.js" "$out\firefox\platform.js"
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
function New-Zip($dir, $dest) {
    $root = (Resolve-Path $dir).Path
    $zip = [IO.Compression.ZipFile]::Open($dest, 'Create')
    try {
        Get-ChildItem $root -Recurse -File | ForEach-Object {
            $name = $_.FullName.Substring($root.Length + 1).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $name) | Out-Null
        }
    } finally { $zip.Dispose() }
}
New-Zip "$out\chrome" "$out\fetchjet-chrome-$version.zip"
New-Zip "$out\firefox" "$out\fetchjet-firefox-$version.xpi"
Write-Host "Built:"; Get-ChildItem $out -File | ForEach-Object { Write-Host "  $($_.FullName)" }
Write-Host "`nFirefox: sign with  npx web-ext sign --channel unlisted --source-dir $out\firefox  (needs AMO API keys)"
