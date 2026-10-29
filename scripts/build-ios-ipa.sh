#!/usr/bin/env bash
# Builds an UNSIGNED FetchJet .ipa for sideloading (AltStore / Sideloadly / TrollStore).
#   ./scripts/build-ios-ipa.sh full    -> FetchJet-full.ipa  (everything)
#   ./scripts/build-ios-ipa.sh store   -> FetchJet-store.ipa (App Store feature set, for testing)
# The App Store submission itself is done from Xcode (Product > Archive) with FETCHJET_EDITION=store.
set -euo pipefail
EDITION="${1:-full}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/ios"
ARCHIVE="$OUT/FetchJet-$EDITION.xcarchive"
mkdir -p "$OUT"

# Native frameworks (Python runtime, libtorrent) are prepared by the first build phase of the
# iosApp target; running it here first gives a readable log when something is missing.
"$ROOT/scripts/ios-prepare-frameworks.sh"

mkdir -p "$ROOT/build"
xcodebuild \
  -project "$ROOT/iosApp/iosApp.xcodeproj" \
  -scheme iosApp \
  -configuration Release \
  -sdk iphoneos \
  -archivePath "$ARCHIVE" \
  -skipPackagePluginValidation \
  -skipMacroValidation \
  FETCHJET_EDITION="$EDITION" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" DEVELOPMENT_TEAM="" \
  archive 2>&1 | tee "$ROOT/build/ios-ipa-$EDITION.log"

APP="$ARCHIVE/Products/Applications/FetchJet.app"
PAYLOAD="$OUT/Payload"
rm -rf "$PAYLOAD" && mkdir -p "$PAYLOAD"
cp -R "$APP" "$PAYLOAD/"
( cd "$OUT" && rm -f "FetchJet-$EDITION.ipa" && zip -qry "FetchJet-$EDITION.ipa" Payload )
rm -rf "$PAYLOAD"
echo "Built: $OUT/FetchJet-$EDITION.ipa"
