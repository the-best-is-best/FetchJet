#!/bin/bash
# Builds LibTorrent.xcframework (libtorrent-rasterbar + the LibTorrent-Swift Objective-C++/Swift
# wrapper used by iTorrent) for iOS device + simulator and drops it into iosApp/Frameworks/.
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

BREW_PREFIX="$(brew --prefix 2>/dev/null || echo /opt/homebrew)"
export PATH="$BREW_PREFIX/bin:$PATH"
if ! command -v cmake >/dev/null; then log "ERROR: cmake not found. Run: brew install cmake boost"; log "DONE_MARKER exit=2"; exit 2; fi
if [ ! -d "$BREW_PREFIX/include/boost" ]; then log "ERROR: Boost headers not found in $BREW_PREFIX/include. Run: brew install boost"; log "DONE_MARKER exit=2"; exit 2; fi
xcodebuild -version 2>&1 | tee -a "$LOG"

TP="$ROOT/iosApp/ThirdParty/LibTorrent-Swift"
if [ ! -f "$TP/LibTorrent.xcodeproj/project.pbxproj" ] || [ ! -f "$TP/Thirdparty/libtorrent/CMakeLists.txt" ]; then
  log "=== Cloning LibTorrent-Swift (with libtorrent submodule) ==="
  rm -rf "$TP"
  run git clone --recursive --depth 1 https://github.com/XITRIX/LibTorrent-Swift "$TP" || { log "DONE_MARKER exit=3"; exit 3; }
  # Plain sources only: a nested .git makes IDEs treat the folder as a second repository.
  rm -rf "$TP/.git" "$TP/Thirdparty/libtorrent/.git" "$TP/Thirdparty/libtorrent/deps/"*/.git 2>/dev/null
fi
cd "$TP" || exit 1

if [ ! -d libtorrent-build/libtorrent.xcodeproj ]; then
  log "=== Generating libtorrent Xcode project (cmake) ==="
  # Same as the wrapper's make.sh, but iOS-only and with deployment targets Xcode 27 accepts
  # (make.sh uses macOS 10.13 and lists watchOS/tvOS/visionOS platforms -> compiler test fails).
  rm -rf libtorrent-build
  run cmake ./Thirdparty/libtorrent \
    -B./libtorrent-build \
    -G Xcode \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_CXX_STANDARD=17 \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=12.0 \
    -DCMAKE_XCODE_ATTRIBUTE_IPHONEOS_DEPLOYMENT_TARGET=16.0 \
    -DCMAKE_XCODE_ATTRIBUTE_MACOSX_DEPLOYMENT_TARGET=12.0 \
    -DCMAKE_XCODE_ATTRIBUTE_SUPPORTED_PLATFORMS="iphonesimulator iphoneos macosx" \
    -DCMAKE_XCODE_ATTRIBUTE_ARCHS="\$(ARCHS_STANDARD)" \
    -DCMAKE_XCODE_ATTRIBUTE_ONLY_ACTIVE_ARCH=NO \
    -DCMAKE_CXX_FLAGS="-DTORRENT_HAVE_MMAP=0 -DNDEBUG" \
    -DBOOST_ROOT="$BREW_PREFIX" \
    -Ddeprecated-functions=OFF \
    -Dwebtorrent=OFF \
    -Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF \
    -DBUILD_SHARED_LIBS=OFF || { log "DONE_MARKER exit=4"; exit 4; }
fi

OUT="$TP/build"
rm -rf "$OUT"
LT_PROJ="$TP/libtorrent-build/libtorrent.xcodeproj"
LT_OUT="$TP/libtorrent-build/Release"   # CONFIGURATION_BUILD_DIR fixed by CMake (same for every platform)

# The wrapper links libtorrent-build/Release/libtorrent-rasterbar.a but has no target dependency on
# the CMake project, so build libtorrent ourselves for each platform right before the wrapper.
build_platform() {   # $1 = sdk (iphonesimulator|iphoneos)  $2 = destination  $3 = archive name
  local sdk="$1" dest="$2" name="$3"
  log "=== libtorrent ($sdk, arm64) ==="
  rm -rf "$LT_OUT"
  run xcodebuild -project "$LT_PROJ" -target torrent-rasterbar -configuration Release -sdk "$sdk" \
    ARCHS=arm64 ONLY_ACTIVE_ARCH=NO IPHONEOS_DEPLOYMENT_TARGET=16.0 MACOSX_DEPLOYMENT_TARGET=12.0 \
    CODE_SIGNING_ALLOWED=NO build || return 1
  [ -f "$LT_OUT/libtorrent-rasterbar.a" ] || { log "ERROR: $LT_OUT/libtorrent-rasterbar.a was not produced"; return 1; }
  log "=== LibTorrent wrapper ($sdk) ==="
  run xcodebuild -project "$TP/LibTorrent.xcodeproj" -scheme LibTorrent -configuration Release \
    -derivedDataPath "$OUT/DerivedData" -destination "$dest" -archivePath "$OUT/$name.xcarchive" \
    ARCHS=arm64 ONLY_ACTIVE_ARCH=NO SKIP_INSTALL=NO BUILD_LIBRARY_FOR_DISTRIBUTION=YES \
    CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" \
    IPHONEOS_DEPLOYMENT_TARGET=16.0 MACOSX_DEPLOYMENT_TARGET=12.0 \
    LIBRARY_SEARCH_PATHS="\$(inherited) $LT_OUT" \
    HEADER_SEARCH_PATHS="\$(inherited) $BREW_PREFIX/include Thirdparty/libtorrent/include" \
    -skipPackagePluginValidation -skipMacroValidation archive || return 1
}

build_platform iphonesimulator 'generic/platform=iOS Simulator' sim || { log "DONE_MARKER exit=5"; exit 5; }
build_platform iphoneos 'generic/platform=iOS' ios || { log "DONE_MARKER exit=6"; exit 6; }

log "=== Creating LibTorrent.xcframework ==="
XC="$ROOT/iosApp/Frameworks/LibTorrent.xcframework"
rm -rf "$XC"
run xcodebuild -create-xcframework \
  -framework "$OUT/sim.xcarchive/Products/Library/Frameworks/LibTorrent.framework" \
  -framework "$OUT/ios.xcarchive/Products/Library/Frameworks/LibTorrent.framework" \
  -output "$XC" || { log "DONE_MARKER exit=7"; exit 7; }

log "=== DONE: $XC ==="
log "DONE_MARKER exit=0"
