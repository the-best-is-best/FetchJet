#!/bin/bash
# mpv 0.42.0 as one self-contained libmpv.2.dylib (arm64): FFmpeg, dav1d, libass, freetype, fribidi, harfbuzz and
# libplacebo linked in statically, only coreaudio for output, then stripped, ad-hoc signed and gzipped.
set -euo pipefail
. "$(dirname "$0")/env.sh"
fj_unpack mpv-0.42.0
# mpv 0.42.0 compiles osdep/utils-mac.c (cfstr_from_cstr, plain C) only with cocoa, but the coreaudio output needs it.
# Without cocoa the library has no Objective-C, so macOS can unload it again after the player closes.
meson_build="$FJ_MPV_SRC/mpv-0.42.0/meson.build"
if ! grep -q "'audio/out/ao_coreaudio_utils.c', 'osdep/utils-mac.c'" "$meson_build"; then
    sed -i '' "s#'audio/out/ao_coreaudio_utils.c')#'audio/out/ao_coreaudio_utils.c', 'osdep/utils-mac.c')#" "$meson_build"
fi
meson setup --wipe "$FJ_MPV_BUILD/mpv" "$FJ_MPV_SRC/mpv-0.42.0" --buildtype=release -Db_ndebug=true \
    -Dauto_features=disabled -Dgpl=false -Dlibmpv=true -Dcplayer=false -Dtests=false \
    -Dcoreaudio=enabled -Dgl=disabled -Dprefer_static=true --default-library=shared --wrap-mode=nodownload \
    -Dc_args="-Os" -Dc_link_args="-Wl,-dead_strip -lc++" --prefix="$FJ_MPV_PREFIX/mpv"
ninja -C "$FJ_MPV_BUILD/mpv"

mkdir -p "$FJ_MPV_DIST"
out="$FJ_MPV_DIST/libmpv.2.dylib"
cp "$FJ_MPV_BUILD/mpv/libmpv.2.dylib" "$out"
chmod u+w "$out"
strip -x "$out"
install_name_tool -id @rpath/libmpv.2.dylib "$out"
codesign --force --sign - "$out"
gzip -9n -c "$out" > "$FJ_MPV_DIST/libmpv-macos-arm64-0.42.0.gz"
otool -L "$out"
ls -l "$out" "$FJ_MPV_DIST/libmpv-macos-arm64-0.42.0.gz"
shasum -a 256 "$FJ_MPV_DIST/libmpv-macos-arm64-0.42.0.gz"
