#!/bin/bash
# Static dav1d, freetype, fribidi, harfbuzz, libass and libplacebo for the macOS arm64 libmpv.
set -euo pipefail
. "$(dirname "$0")/env.sh"

meson_static() {
    local name=$1; shift
    fj_unpack "$name"
    rm -rf "$FJ_MPV_BUILD/$name"
    meson setup "$FJ_MPV_BUILD/$name" "$FJ_MPV_SRC/$name" $FJ_MPV_MESON_STATIC "$@"
    ninja -C "$FJ_MPV_BUILD/$name"
    ninja -C "$FJ_MPV_BUILD/$name" install
}

meson_static dav1d-1.5.1 -Denable_tools=false -Denable_tests=false -Denable_examples=false

meson_static freetype-2.13.3 -Dbrotli=disabled -Dbzip2=disabled -Dharfbuzz=disabled -Dpng=disabled -Dzlib=disabled

meson_static fribidi-1.0.18 -Ddocs=false -Dbin=false -Dtests=false

meson_static harfbuzz-15.2.0 -Dauto_features=disabled -Dtests=disabled -Ddocs=disabled -Dutilities=disabled

meson_static libass-0.17.5 -Dauto_features=disabled -Dcoretext=enabled -Dasm=disabled

meson_static libplacebo-7.387.0 -Dauto_features=disabled -Dvulkan=disabled -Dopengl=disabled -Dd3d11=disabled \
    -Ddemos=false -Dtests=false
