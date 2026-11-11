#!/bin/bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
LIBTORRENT_COMMIT="6da363d2994f17c0b3c0450d124cf73a31a73847"
OPENSSL_VERSION="3.5.9"
OPENSSL_SHA256="603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a"
MIN_MACOS="${FJT_MIN_MACOS:-11.0}"
CACHE="${FJT_CACHE:-$HOME/Library/Caches/fetchjet-libtorrent}"
BUILD="${FJT_BUILD_DIR:-$HERE/build-mac}"
JOBS="${FJT_JOBS:-$(sysctl -n hw.ncpu)}"
BREW_PREFIX="$(brew --prefix 2>/dev/null || echo /opt/homebrew)"
export PATH="$BREW_PREFIX/bin:$PATH"

command -v cmake >/dev/null || { echo "ERROR: cmake not found (brew install cmake)"; exit 2; }
BOOST_ROOT="${BOOST_ROOT:-$BREW_PREFIX}"
[ -f "$BOOST_ROOT/include/boost/version.hpp" ] || { echo "ERROR: Boost headers not found in $BOOST_ROOT/include (brew install boost)"; exit 2; }

mkdir -p "$CACHE"
LT_SRC="${LIBTORRENT_SOURCE_DIR:-$CACHE/libtorrent-${LIBTORRENT_COMMIT:0:7}}"
if [ ! -f "$LT_SRC/CMakeLists.txt" ]; then
  echo "=== libtorrent $LIBTORRENT_COMMIT -> $LT_SRC"
  rm -rf "$LT_SRC"
  mkdir -p "$LT_SRC"
  (
    cd "$LT_SRC"
    git init -q
    git remote add origin https://github.com/arvidn/libtorrent.git
    git fetch -q --depth 1 origin "$LIBTORRENT_COMMIT"
    git checkout -q FETCH_HEAD
    git submodule update -q --init --recursive --depth 1
  )
fi

SSL_PREFIX="$CACHE/openssl-$OPENSSL_VERSION-arm64-macos$MIN_MACOS"
if [ ! -f "$SSL_PREFIX/lib/libssl.a" ]; then
  echo "=== OpenSSL $OPENSSL_VERSION (static, macOS $MIN_MACOS) -> $SSL_PREFIX"
  TARBALL="$CACHE/openssl-$OPENSSL_VERSION.tar.gz"
  if [ ! -f "$TARBALL" ]; then
    curl -fL -o "$TARBALL.part" "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz"
    mv "$TARBALL.part" "$TARBALL"
  fi
  echo "$OPENSSL_SHA256  $TARBALL" | shasum -a 256 -c - || { rm -f "$TARBALL"; echo "ERROR: OpenSSL checksum mismatch"; exit 3; }
  SSL_WORK="$CACHE/openssl-$OPENSSL_VERSION-src"
  rm -rf "$SSL_WORK"
  mkdir -p "$SSL_WORK"
  tar -xzf "$TARBALL" -C "$SSL_WORK" --strip-components 1
  (
    cd "$SSL_WORK"
    ./Configure darwin64-arm64-cc no-shared no-tests no-docs no-apps no-module no-legacy \
      -mmacosx-version-min="$MIN_MACOS" --prefix="$SSL_PREFIX" --libdir=lib
    make -j"$JOBS" build_libs >/dev/null
    make install_dev >/dev/null
  )
  rm -rf "$SSL_WORK"
fi

echo "=== fjtorrent -> $BUILD"
cmake -S "$HERE" -B "$BUILD" -G "Unix Makefiles" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_STANDARD=17 \
  -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_OSX_DEPLOYMENT_TARGET="$MIN_MACOS" \
  -DLIBTORRENT_SOURCE_DIR="$LT_SRC" \
  -DBOOST_ROOT="$BOOST_ROOT" \
  -DBoost_INCLUDE_DIR="$BOOST_ROOT/include" \
  -DOPENSSL_ROOT_DIR="$SSL_PREFIX" \
  -DOPENSSL_USE_STATIC_LIBS=ON \
  -DBUILD_SHARED_LIBS=OFF \
  -Ddeprecated-functions=OFF \
  -Dwebtorrent=OFF \
  -Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF
cmake --build "$BUILD" -j "$JOBS" --target fjtorrent fjtseed "$@"

DYLIB="$BUILD/libfjtorrent.dylib"
[ -f "$DYLIB" ] || { echo "ERROR: $DYLIB not produced"; exit 4; }
codesign --force --sign - "$DYLIB"
echo "=== DONE: $DYLIB"
otool -L "$DYLIB"
