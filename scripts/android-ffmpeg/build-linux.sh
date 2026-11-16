#!/bin/bash
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
NDK=${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:?set ANDROID_NDK_ROOT}}
case "$(uname -s)" in Darwin) TAG=darwin-x86_64; JOBS=$(sysctl -n hw.ncpu);; *) TAG=linux-x86_64; JOBS=$(nproc);; esac
TC=$NDK/toolchains/llvm/prebuilt/$TAG
ROOT=${FFROOT:-$HERE/../../build/android-ffmpeg}
OUT=${FFOUT:-$HERE/../../androidApp/ffmpeg-libs}
ABIS=${*:-arm64-v8a armeabi-v7a x86_64 x86}
mkdir -p "$ROOT/src" "$ROOT/bin"
ROOT=$(cd "$ROOT" && pwd)
OUT=$(mkdir -p "$OUT" && cd "$OUT" && pwd)
fetch() { [ -f "$ROOT/src/$1" ] || curl -sSL --retry 3 -o "$ROOT/src/$1" "$2"; }
fetch ffmpeg.tar.xz https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz
fetch lame.tar.gz https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz
fetch opus.tar.gz https://downloads.xiph.org/releases/opus/opus-1.5.2.tar.gz
cp "$HERE/bin/pkg-config" "$ROOT/bin/pkg-config"
. "$HERE/ffmpeg-config.sh"
export PATH="$TC/bin:$PATH"
for ABI in $ABIS; do
  EXTRA=""
  case $ABI in
    arm64-v8a) TRIPLE=aarch64-linux-android24; HOST=aarch64-linux-gnu; ARCH=aarch64;;
    armeabi-v7a) TRIPLE=armv7a-linux-androideabi24; HOST=arm-linux-gnueabi; ARCH=arm;;
    x86) TRIPLE=i686-linux-android24; HOST=i686-linux-gnu; ARCH=x86; EXTRA=--disable-asm;;
    x86_64) TRIPLE=x86_64-linux-android24; HOST=x86_64-linux-gnu; ARCH=x86_64;;
  esac
  W=$ROOT/work/$ABI; PREFIX=$ROOT/out/$ABI
  rm -rf "$W"; mkdir -p "$W" "$PREFIX"
  printf '#!/bin/sh\nexec %s/bin/clang --target=%s "$@"\n' "$TC" "$TRIPLE" > "$ROOT/bin/cc-$ABI"
  chmod +x "$ROOT/bin/cc-$ABI"
  export CC="$ROOT/bin/cc-$ABI" AR=$TC/bin/llvm-ar RANLIB=$TC/bin/llvm-ranlib STRIP=$TC/bin/llvm-strip
  cd "$W"
  tar xzf "$ROOT/src/lame.tar.gz"; tar xzf "$ROOT/src/opus.tar.gz"
  (cd lame-3.100 && ./configure --host=$HOST --prefix=$PREFIX --disable-shared --enable-static --disable-frontend --disable-decoder --disable-gtktest > "$W/lame.log" 2>&1 && make -j"$JOBS" >> "$W/lame.log" 2>&1 && make install >> "$W/lame.log" 2>&1)
  (cd opus-1.5.2 && ./configure --host=$HOST --prefix=$PREFIX --disable-shared --enable-static --disable-doc --disable-extra-programs > "$W/opus.log" 2>&1 && make -j"$JOBS" >> "$W/opus.log" 2>&1 && make install >> "$W/opus.log" 2>&1)
  tar xJf "$ROOT/src/ffmpeg.tar.xz"
  mkdir ffb; cd ffb
  export FFPREFIX=$PREFIX
  ../ffmpeg-7.1.1/configure --prefix="$ROOT/ffout/$ABI" --target-os=android --arch=$ARCH \
    --enable-cross-compile --cc="$CC" --host-cc=${HOST_CC:-cc} --ar="$AR" --ranlib="$RANLIB" --strip="$STRIP" --nm="$TC/bin/llvm-nm" \
    --disable-everything --disable-doc --disable-shared --enable-static --disable-autodetect --enable-zlib \
    --disable-avdevice --disable-network --disable-debug --enable-small --enable-pic $EXTRA \
    --enable-ffmpeg --enable-ffprobe --disable-ffplay --enable-libmp3lame --enable-libopus \
    --pkg-config="$ROOT/bin/pkg-config" \
    --extra-cflags="-I$PREFIX/include -Os -ffunction-sections -fdata-sections" \
    --extra-ldexeflags="-pie -Wl,--export-dynamic-symbol=main" \
    --extra-ldflags="-L$PREFIX/lib -Wl,--gc-sections -Wl,-z,max-page-size=16384" \
    --enable-protocol=file,pipe,fd,cache,data,concat,subfile \
    --enable-demuxer=$DEMUX --enable-muxer=$MUX --enable-decoder=$DEC --enable-encoder=$ENC \
    --enable-parser=$PARS --enable-bsf=$BSF --enable-filter=$FILT > "$W/configure.log" 2>&1
  make -j"$JOBS" > "$W/make.log" 2>&1
  mkdir -p "$OUT/$ABI"
  cp ffmpeg "$OUT/$ABI/libffmpeg.so"; cp ffprobe "$OUT/$ABI/libffprobe.so"
  echo "built $ABI"
done
