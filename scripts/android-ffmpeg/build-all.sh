#!/bin/bash
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=${FFROOT:-/e/tools/ffbuild}
mkdir -p $ROOT/src $ROOT/bin
cp $HERE/bin/pkg-config $ROOT/bin/pkg-config
[ -f $ROOT/src/ffmpeg.tar.xz ] || curl -sSL -o $ROOT/src/ffmpeg.tar.xz https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz
[ -f $ROOT/src/lame.tar.gz ] || curl -sSL -o $ROOT/src/lame.tar.gz https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz
[ -f $ROOT/src/opus.tar.gz ] || curl -sSL -o $ROOT/src/opus.tar.gz https://downloads.xiph.org/releases/opus/opus-1.5.2.tar.gz
for a in arm64-v8a armeabi-v7a x86_64 x86; do
  /c/msys64/usr/bin/bash.exe $HERE/build-deps.sh $a
  /c/msys64/usr/bin/bash.exe $HERE/build-ffmpeg.sh $a
  mkdir -p $HERE/../../androidApp/ffmpeg-libs/$a
  cp $ROOT/work/$a/ffb/ffmpeg $HERE/../../androidApp/ffmpeg-libs/$a/libffmpeg.so
  cp $ROOT/work/$a/ffb/ffprobe $HERE/../../androidApp/ffmpeg-libs/$a/libffprobe.so
done
