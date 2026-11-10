#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
HOST_BUILD="${FJ_HOST_BUILD:-$HOME/fetchjet-native/host-build}"
SLINT_DIR="${FJ_SLINT_DIR:-$HOME/fetchjet-native/slint}"
OUT="${FJ_PACKAGE_OUT:-$HOME/fetchjet-native/package}"
CORE_KIND="${FJ_CORE_KIND:-releaseShared}"
CORE="${FJ_CORE:-$REPO/desktopNative/core/build/bin/linuxX64/$CORE_KIND/libfjcore.so}"
TORRENT="${FJ_TORRENT_SO:-}"

fail() { echo "build-linux: FAILED: $*" >&2; exit 1; }
need() { [ -e "$1" ] || fail "missing $1"; }

VERSION="$(sed -n 's/.*APP_VERSION *= *"\([^"]*\)".*/\1/p' "$REPO/sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/core/update/AppVersion.kt" | head -1)"
EXT_VERSION="$(sed -n 's/.*EXTENSION_VERSION *= *"\([^"]*\)".*/\1/p' "$REPO/sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/core/update/AppVersion.kt" | head -1)"
[ -n "$VERSION" ] || fail "APP_VERSION not found"
MANIFEST_VERSION="$(sed -n 's/.*"version" *: *"\([^"]*\)".*/\1/p' "$REPO/extension/manifest.json" | head -1)"
[ "$MANIFEST_VERSION" = "$EXT_VERSION" ] || fail "extension/manifest.json $MANIFEST_VERSION != EXTENSION_VERSION $EXT_VERSION"

need "$HOST_BUILD/FetchJet"
need "$HOST_BUILD/libslint_cpp.so"
need "$CORE"
[ "$CORE" -nt "$HOST_BUILD/FetchJet" ] && echo "build-linux: note: the core is newer than the host; the host only loads it, so that is fine"

NAME="FetchJet-$VERSION-linux-x64"
STAGE="$OUT/$NAME"
rm -rf "$STAGE" "$OUT/deb-root"
mkdir -p "$STAGE/extension/chromium" "$STAGE/licenses"

install -m 755 "$HOST_BUILD/FetchJet" "$STAGE/FetchJet"
install -m 644 "$HOST_BUILD/libslint_cpp.so" "$STAGE/libslint_cpp.so"
install -m 644 "$CORE" "$STAGE/libfjcore.so"
[ -n "$TORRENT" ] && install -m 644 "$TORRENT" "$STAGE/libfjtorrent.so"
strip --strip-unneeded "$STAGE/FetchJet" 2>/dev/null || true

for f in background.js yt-core.js content.js popup.html popup.js options.html options.js icon16.png icon48.png icon128.png manifest.json platform.js; do
    need "$REPO/extension/$f"
    install -m 644 "$REPO/extension/$f" "$STAGE/extension/chromium/$f"
done
for t in i18n _locales; do
    need "$REPO/extension/$t"
    cp -r "$REPO/extension/$t" "$STAGE/extension/chromium/$t"
done

for t in fonts emoji; do
    ls "$REPO/desktopNative/res/$t"/* >/dev/null 2>&1 || fail "missing desktopNative/res/$t"
    mkdir -p "$STAGE/$t"
    install -m 644 "$REPO/desktopNative/res/$t"/* "$STAGE/$t/"
done
install -m 644 "$REPO/LICENSE" "$STAGE/licenses/FetchJet-LICENSE.txt"
install -m 644 "$REPO/desktopNative/packaging/licenses/THIRD-PARTY.txt" "$STAGE/licenses/THIRD-PARTY.txt"
install -m 644 "$REPO/desktopNative/packaging/licenses/Apache-2.0.txt" "$STAGE/licenses/Apache-2.0.txt"
if [ -d "$SLINT_DIR/licenses" ]; then
    mkdir -p "$STAGE/licenses/slint"
    cp "$SLINT_DIR"/licenses/* "$STAGE/licenses/slint/" 2>/dev/null || true
fi

ICON="$REPO/desktopNative/packaging/msix/Assets/Square150x150Logo.scale-200.png"
need "$ICON"
install -m 644 "$ICON" "$STAGE/fetchjet.png"
: > "$STAGE/FetchJet.installed"

cat > "$STAGE/fetchjet.desktop" <<EOF_DESKTOP
[Desktop Entry]
Type=Application
Name=FetchJet
GenericName=Download Manager
Comment=Fast downloads: files, videos and torrents
Exec=FetchJet %u
Icon=fetchjet
Terminal=false
Categories=Network;FileTransfer;
MimeType=x-scheme-handler/fetchjet;application/x-bittorrent;x-scheme-handler/magnet;
StartupWMClass=FetchJet
EOF_DESKTOP

tar -C "$OUT" -czf "$OUT/$NAME.tar.gz" "$NAME"

ROOT="$OUT/deb-root"
mkdir -p "$ROOT/DEBIAN" "$ROOT/opt" "$ROOT/usr/bin" "$ROOT/usr/share/applications" "$ROOT/usr/share/icons/hicolor/256x256/apps"
cp -a "$STAGE" "$ROOT/opt/fetchjet"
rm -f "$ROOT/opt/fetchjet/fetchjet.desktop"
ln -s /opt/fetchjet/FetchJet "$ROOT/usr/bin/fetchjet"
sed 's|^Exec=FetchJet|Exec=/opt/fetchjet/FetchJet|' "$STAGE/fetchjet.desktop" > "$ROOT/usr/share/applications/fetchjet.desktop"
install -m 644 "$ICON" "$ROOT/usr/share/icons/hicolor/256x256/apps/fetchjet.png"
SIZE_KB="$(du -sk "$ROOT/opt" | cut -f1)"
cat > "$ROOT/DEBIAN/control" <<EOF_CONTROL
Package: fetchjet
Version: $VERSION
Section: net
Priority: optional
Architecture: amd64
Installed-Size: $SIZE_KB
Maintainer: FetchJet <fetchjet@users.noreply.github.com>
Depends: libc6 (>= 2.31), libstdc++6, libgcc-s1, libcurl4t64 | libcurl4, libssl3t64 | libssl3, libfontconfig1, libfreetype6, libxkbcommon0, libinput10, libudev1, libgbm1, zlib1g
Recommends: xdg-utils, libnotify-bin, zenity | kdialog, wl-clipboard | xclip
Description: FetchJet download manager (native build)
 Multi-connection downloads, video downloads through yt-dlp and a browser
 extension bridge. Native build: Kotlin/Native core with a Slint window.
EOF_CONTROL
dpkg-deb --root-owner-group --build "$ROOT" "$OUT/fetchjet_${VERSION}_amd64.deb" > /dev/null
rm -rf "$ROOT"

echo "build-linux: $OUT/$NAME.tar.gz ($(du -h "$OUT/$NAME.tar.gz" | cut -f1))"
echo "build-linux: $OUT/fetchjet_${VERSION}_amd64.deb ($(du -h "$OUT/fetchjet_${VERSION}_amd64.deb" | cut -f1))"
