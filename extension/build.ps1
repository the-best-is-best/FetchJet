# Packages the extension for Chrome/Edge (zip) and Firefox (xpi) on Windows.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$version = (Get-Content manifest.json -Raw | ConvertFrom-Json).version
$out = Join-Path $PSScriptRoot "..\build\extension"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path "$out\chrome", "$out\firefox" | Out-Null
$files = "background.js","yt-core.js","content.js","popup.html","popup.js","options.html","options.js","icon16.png","icon48.png","icon128.png"
Copy-Item ($files + "manifest.json") "$out\chrome\"
Copy-Item $files "$out\firefox\"
Copy-Item "manifest.firefox.json" "$out\firefox\manifest.json"
Compress-Archive -Path "$out\chrome\*" -DestinationPath "$out\fetchjet-chrome-$version.zip" -Force
Compress-Archive -Path "$out\firefox\*" -DestinationPath "$out\fetchjet-firefox-$version.zip" -Force
Rename-Item "$out\fetchjet-firefox-$version.zip" "fetchjet-firefox-$version.xpi" -Force
Write-Host "Built:"; Get-ChildItem $out -File | ForEach-Object { Write-Host "  $($_.FullName)" }
Write-Host "`nFirefox: sign with  npx web-ext sign --channel unlisted --source-dir $out\firefox  (needs AMO API keys)"
