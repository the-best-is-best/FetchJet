#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
NATIVE="$REPO/desktopNative"
SLINT_DIR="${FJ_SLINT_DIR:-$HOME/tools/slint/Slint-cpp-1.18.1-Darwin-arm64}"
HOST_BUILD="${FJ_HOST_BUILD:-$NATIVE/build-mac}"
OUT="${FJ_PACKAGE_OUT:-$NATIVE/build-mac/package}"
CMAKE="${CMAKE:-$(command -v cmake || echo /opt/homebrew/bin/cmake)}"
SKIP_CORE="${FJ_SKIP_CORE:-0}"

fail() { echo "make-dmg: FAILED: $*" >&2; exit 1; }
need() { [ -e "$1" ] || fail "missing $1"; }

[ "$(uname -s)" = "Darwin" ] || fail "run this on a Mac"
[ "$(uname -m)" = "arm64" ] || fail "Apple Silicon only"
need "$SLINT_DIR/lib/cmake/Slint"

VERSION_FILE="$REPO/sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/core/update/AppVersion.kt"
VERSION="$(sed -n 's/.*APP_VERSION *= *"\([^"]*\)".*/\1/p' "$VERSION_FILE" | head -1)"
EXT_VERSION="$(sed -n 's/.*EXTENSION_VERSION *= *"\([^"]*\)".*/\1/p' "$VERSION_FILE" | head -1)"
[ -n "$VERSION" ] || fail "APP_VERSION not found"
MANIFEST_VERSION="$(sed -n 's/.*"version" *: *"\([^"]*\)".*/\1/p' "$REPO/extension/manifest.json" | head -1)"
[ "$MANIFEST_VERSION" = "$EXT_VERSION" ] || fail "extension/manifest.json $MANIFEST_VERSION != EXTENSION_VERSION $EXT_VERSION"

if [ "$SKIP_CORE" != "1" ]; then
    echo "make-dmg: core (Kotlin/Native release)"
    (cd "$REPO" && ./gradlew :desktopNativeCore:linkReleaseSharedMacosArm64 -Pfetchjet.nativeDesktop=true)
fi
CORE="$NATIVE/core/build/bin/macosArm64/releaseShared/libfjcore.dylib"
need "$CORE"

echo "make-dmg: host (cmake, Release)"
"$CMAKE" -S "$NATIVE" -B "$HOST_BUILD" -DCMAKE_BUILD_TYPE=Release -DSLINT_DIR="$SLINT_DIR" -DFJ_CORE_KIND=releaseShared
"$CMAKE" --build "$HOST_BUILD" -j "$(sysctl -n hw.ncpu)"

BUILT_APP="$HOST_BUILD/FetchJet.app"
need "$BUILT_APP/Contents/MacOS/FetchJet"
need "$BUILT_APP/Contents/MacOS/libfjcore.dylib"
need "$BUILT_APP/Contents/MacOS/libslint_cpp.dylib"

STAGE="$OUT/stage"
APP="$STAGE/FetchJet.app"
rm -rf "$STAGE"
mkdir -p "$STAGE"
ditto "$BUILT_APP" "$APP"
rm -rf "$APP/Contents/_CodeSignature"

MACOS="$APP/Contents/MacOS"
install -m 755 "$CORE" "$MACOS/libfjcore.dylib"
install_name_tool -id @rpath/libfjcore.dylib "$MACOS/libfjcore.dylib" 2>/dev/null
mkdir -p "$MACOS/extension/chromium" "$APP/Contents/Resources/licenses"
for f in background.js yt-core.js content.js popup.html popup.js options.html options.js icon16.png icon48.png icon128.png manifest.json platform.js; do
    need "$REPO/extension/$f"
    install -m 644 "$REPO/extension/$f" "$MACOS/extension/chromium/$f"
done
for t in i18n _locales; do
    need "$REPO/extension/$t"
    cp -R "$REPO/extension/$t" "$MACOS/extension/chromium/$t"
done
install -m 644 "$REPO/LICENSE" "$APP/Contents/Resources/licenses/FetchJet-LICENSE.txt"
install -m 644 "$NATIVE/packaging/licenses/THIRD-PARTY.txt" "$APP/Contents/Resources/licenses/THIRD-PARTY.txt"
install -m 644 "$NATIVE/packaging/licenses/Apache-2.0.txt" "$APP/Contents/Resources/licenses/Apache-2.0.txt"
if [ -d "$SLINT_DIR/licenses" ]; then
    mkdir -p "$APP/Contents/Resources/licenses/slint"
    cp "$SLINT_DIR"/licenses/* "$APP/Contents/Resources/licenses/slint/" 2>/dev/null || true
fi
: > "$APP/Contents/Resources/FetchJet.installed"
strip -x "$MACOS/FetchJet" 2>/dev/null || true
xattr -cr "$APP"

echo "make-dmg: ad-hoc codesign"
for lib in "$MACOS"/*.dylib; do
    codesign --force --sign - "$lib"
done
codesign --force --deep --sign - "$APP"
codesign --verify --deep --strict "$APP" || fail "codesign --verify failed"

DMG="$OUT/FetchJet-$VERSION-arm64.dmg"
DMG_ROOT="$OUT/dmg-root"
rm -rf "$DMG_ROOT" "$DMG"
mkdir -p "$DMG_ROOT"
ditto "$APP" "$DMG_ROOT/FetchJet.app"
ln -s /Applications "$DMG_ROOT/Applications"
hdiutil create -volname "FetchJet $VERSION" -srcfolder "$DMG_ROOT" -fs HFS+ -format UDZO -ov "$DMG" > /dev/null
rm -rf "$DMG_ROOT"
hdiutil verify "$DMG" > /dev/null || fail "hdiutil verify failed"

echo "make-dmg: $APP"
echo "make-dmg: $DMG ($(du -h "$DMG" | cut -f1))"
