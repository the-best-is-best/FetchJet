#!/bin/bash
# Builds FJTorrent.xcframework (libtorrent-rasterbar + FetchJet's own Objective-C++ wrapper in
# iosApp/FJTorrent) for iOS device + simulator and drops it into iosApp/Frameworks/.
# Double-click in Finder or run from Terminal. Log: build/libtorrent-ios.log
#
# Prerequisites (one time):   brew install cmake boost
set -o pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1
mkdir -p build iosApp/Frameworks iosApp/ThirdParty
LOG="$ROOT/build/libtorrent-ios.log"
: > "$LOG"
log() { echo "$@" | tee -a "$LOG"; }
run() { log "+ $*"; "$@" 2>&1 | tee -a "$LOG"; return ${PIPESTATUS[0]}; }

LIBTORRENT_COMMIT="6da363d2994f17c0b3c0450d124cf73a31a73847"

BREW_PREFIX="$(brew --prefix 2>/dev/null || echo /opt/homebrew)"
export PATH="$BREW_PREFIX/bin:$PATH"
if ! command -v cmake >/dev/null; then log "ERROR: cmake not found. Run: brew install cmake boost"; log "DONE_MARKER exit=2"; exit 2; fi
if [ ! -d "$BREW_PREFIX/include/boost" ]; then log "ERROR: Boost headers not found in $BREW_PREFIX/include. Run: brew install boost"; log "DONE_MARKER exit=2"; exit 2; fi
xcodebuild -version 2>&1 | tee -a "$LOG"

LT_SRC="$ROOT/iosApp/ThirdParty/libtorrent"
if [ ! -f "$LT_SRC/CMakeLists.txt" ]; then
  log "=== Fetching libtorrent $LIBTORRENT_COMMIT ==="
  rm -rf "$LT_SRC"
  mkdir -p "$LT_SRC"
  ( cd "$LT_SRC" &&
    run git init -q &&
    run git remote add origin https://github.com/arvidn/libtorrent.git &&
    run git fetch --depth 1 origin "$LIBTORRENT_COMMIT" &&
    run git checkout -q FETCH_HEAD &&
    run git submodule update --init --recursive --depth 1 ) || { log "DONE_MARKER exit=3"; exit 3; }
  rm -rf "$LT_SRC/.git" "$LT_SRC/deps/"*/.git 2>/dev/null
fi

BUILD="$ROOT/build/fjtorrent"
FLAGS="-DTORRENT_HAVE_MMAP=0 -DNDEBUG"
if [ ! -d "$BUILD/xcode/FJTorrent.xcodeproj" ]; then
  log "=== Generating Xcode project (cmake) ==="
  rm -rf "$BUILD"
  run cmake "$ROOT/iosApp/FJTorrent" \
    -B"$BUILD/xcode" \
    -G Xcode \
    -DLIBTORRENT_SOURCE_DIR="$LT_SRC" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_CXX_STANDARD=17 \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=12.0 \
    -DCMAKE_XCODE_ATTRIBUTE_IPHONEOS_DEPLOYMENT_TARGET=16.0 \
    -DCMAKE_XCODE_ATTRIBUTE_MACOSX_DEPLOYMENT_TARGET=12.0 \
    -DCMAKE_XCODE_ATTRIBUTE_SUPPORTED_PLATFORMS="iphonesimulator iphoneos macosx" \
    -DCMAKE_XCODE_ATTRIBUTE_ARCHS="\$(ARCHS_STANDARD)" \
    -DCMAKE_XCODE_ATTRIBUTE_ONLY_ACTIVE_ARCH=NO \
    -DCMAKE_CXX_FLAGS="$FLAGS" \
    -DCMAKE_OBJCXX_FLAGS="$FLAGS" \
    -DBOOST_ROOT="$BREW_PREFIX" \
    -Ddeprecated-functions=OFF \
    -Dwebtorrent=OFF \
    -Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF \
    -DBUILD_SHARED_LIBS=OFF || { log "DONE_MARKER exit=4"; exit 4; }
fi

PROJ="$BUILD/xcode/FJTorrent.xcodeproj"
HEADERS="$BUILD/headers"
rm -rf "$HEADERS"
mkdir -p "$HEADERS/FJTorrent"
cp "$ROOT/iosApp/FJTorrent/include/"* "$HEADERS/FJTorrent/"

build_platform() {   # $1 = sdk (iphonesimulator|iphoneos)
  local sdk="$1"
  log "=== FJTorrent + libtorrent ($sdk, arm64) ==="
  rm -rf "$BUILD/xcode/Release" "$BUILD/xcode/libtorrent/Release"
  run xcodebuild -project "$PROJ" -target FJTorrent -configuration Release -sdk "$sdk" \
    ARCHS=arm64 ONLY_ACTIVE_ARCH=NO IPHONEOS_DEPLOYMENT_TARGET=16.0 MACOSX_DEPLOYMENT_TARGET=12.0 \
    CODE_SIGNING_ALLOWED=NO build || return 1
  local wrapper lt
  wrapper="$(find "$BUILD/xcode" -name libFJTorrent.a -path '*Release*' | head -1)"
  lt="$(find "$BUILD/xcode" -name libtorrent-rasterbar.a -path '*Release*' | head -1)"
  [ -f "$wrapper" ] && [ -f "$lt" ] || { log "ERROR: libFJTorrent.a / libtorrent-rasterbar.a not produced"; return 1; }
  mkdir -p "$BUILD/$sdk"
  run libtool -static -o "$BUILD/$sdk/libFJTorrent.a" "$wrapper" "$lt" || return 1
}

build_platform iphonesimulator || { log "DONE_MARKER exit=5"; exit 5; }
build_platform iphoneos || { log "DONE_MARKER exit=6"; exit 6; }

log "=== Creating FJTorrent.xcframework ==="
XC="$ROOT/iosApp/Frameworks/FJTorrent.xcframework"
rm -rf "$XC" "$ROOT/iosApp/Frameworks/LibTorrent.xcframework"
run xcodebuild -create-xcframework \
  -library "$BUILD/iphonesimulator/libFJTorrent.a" -headers "$HEADERS" \
  -library "$BUILD/iphoneos/libFJTorrent.a" -headers "$HEADERS" \
  -output "$XC" || { log "DONE_MARKER exit=7"; exit 7; }

log "=== DONE: $XC ==="
log "DONE_MARKER exit=0"
