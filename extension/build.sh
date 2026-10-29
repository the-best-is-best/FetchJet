#!/usr/bin/env bash
# Packages the extension for Chrome/Edge (zip) and Firefox (xpi = zip with the Firefox manifest).
set -euo pipefail
cd "$(dirname "$0")"
VERSION=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' manifest.json | head -1)
OUT=../build/extension
rm -rf "$OUT"; mkdir -p "$OUT/chrome" "$OUT/firefox"
FILES="background.js yt-core.js content.js popup.html popup.js options.html options.js icon16.png icon48.png icon128.png"
cp $FILES manifest.json "$OUT/chrome/"
cp $FILES "$OUT/firefox/"; cp manifest.firefox.json "$OUT/firefox/manifest.json"
( cd "$OUT/chrome"  && zip -q -r "../fetchjet-chrome-$VERSION.zip" . )
( cd "$OUT/firefox" && zip -q -r "../fetchjet-firefox-$VERSION.xpi" . )
echo "Built:"; ls -1 "$OUT"/*.zip "$OUT"/*.xpi
echo
echo "Firefox: an unsigned .xpi only installs on Developer Edition / Nightly with"
echo "xpinstall.signatures.required=false, or temporarily via about:debugging."
echo "For normal Firefox, sign it: npx web-ext sign --channel unlisted --source-dir $OUT/firefox"
