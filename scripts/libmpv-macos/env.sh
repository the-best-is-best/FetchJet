# Shared settings for the macOS arm64 libmpv build. Source it, do not run it.
# FJ_MPV_WORK: work folder (sources in $FJ_MPV_WORK/dl as tarballs, build trees and the result go there too).
# Needs: Xcode command line tools, meson + ninja (e.g. a python venv in $FJ_MPV_WORK/venv), pkgconf.
: "${FJ_MPV_WORK:?set FJ_MPV_WORK to a work folder}"
export FJ_MPV_WORK
export FJ_MPV_SRC="$FJ_MPV_WORK/src"
export FJ_MPV_BUILD="$FJ_MPV_WORK/build"
export FJ_MPV_PREFIX="$FJ_MPV_WORK/out"
export FJ_MPV_DIST="$FJ_MPV_WORK/dist"
export MACOSX_DEPLOYMENT_TARGET=12.0
if [ -x "$FJ_MPV_WORK/venv/bin/meson" ]; then
    export PATH="$FJ_MPV_WORK/venv/bin:$PATH"
fi
export PKG_CONFIG_PATH="$FJ_MPV_PREFIX/lib/pkgconfig"
export PKG_CONFIG_LIBDIR="$FJ_MPV_PREFIX/lib/pkgconfig"
export CC=clang CXX=clang++
export CFLAGS="-arch arm64 -mmacosx-version-min=$MACOSX_DEPLOYMENT_TARGET -O2 -ffunction-sections -fdata-sections -I$FJ_MPV_PREFIX/include"
export CXXFLAGS="$CFLAGS"
export LDFLAGS="-arch arm64 -mmacosx-version-min=$MACOSX_DEPLOYMENT_TARGET"
FJ_MPV_MESON_STATIC="--buildtype=release -Db_ndebug=true --default-library=static --prefix=$FJ_MPV_PREFIX --libdir=lib --wrap-mode=nodownload"
export FJ_MPV_MESON_STATIC

fj_unpack() {
    local name=$1
    mkdir -p "$FJ_MPV_SRC"
    if [ ! -d "$FJ_MPV_SRC/$name" ]; then
        tar -xf "$FJ_MPV_WORK/dl/$name".tar.* -C "$FJ_MPV_SRC"
    fi
}
