#!/bin/bash
# Downloads everything the macOS arm64 libmpv build needs into $FJ_MPV_WORK (about 50 MB):
# source tarballs to dl/, meson + ninja + jinja2 into a python venv, header-only fast_float and Vulkan headers
# (libplacebo compiles its Vulkan stubs even with Vulkan off). pkgconf must be on PATH (brew install pkgconf).
set -euo pipefail
. "$(dirname "$0")/env.sh"
mkdir -p "$FJ_MPV_WORK/dl"
cd "$FJ_MPV_WORK/dl"
get() {
    [ -f "$2" ] || curl -sSfL -o "$2" "$1"
}
get https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz ffmpeg-7.1.1.tar.xz
get https://downloads.videolan.org/pub/videolan/dav1d/1.5.1/dav1d-1.5.1.tar.xz dav1d-1.5.1.tar.xz
get https://github.com/mpv-player/mpv/archive/refs/tags/v0.42.0.tar.gz mpv-0.42.0.tar.gz
get https://github.com/libass/libass/releases/download/0.17.5/libass-0.17.5.tar.xz libass-0.17.5.tar.xz
get https://github.com/fribidi/fribidi/releases/download/v1.0.18/fribidi-1.0.18.tar.xz fribidi-1.0.18.tar.xz
get https://github.com/harfbuzz/harfbuzz/releases/download/15.2.0/harfbuzz-15.2.0.tar.xz harfbuzz-15.2.0.tar.xz
get https://download.savannah.gnu.org/releases/freetype/freetype-2.13.3.tar.xz freetype-2.13.3.tar.xz
get https://github.com/haasn/libplacebo/archive/refs/tags/v7.387.0.tar.gz libplacebo-7.387.0.tar.gz
get https://github.com/fastfloat/fast_float/releases/download/v8.4.0/fast_float.h fast_float-8.4.0.h

include="$FJ_MPV_PREFIX/include"
mkdir -p "$include/fast_float" "$include/vulkan" "$include/vk_video"
cp fast_float-8.4.0.h "$include/fast_float/fast_float.h"
vk=https://raw.githubusercontent.com/KhronosGroup/Vulkan-Headers/vulkan-sdk-1.4.363.0/include
for f in vulkan.h vulkan_core.h vk_platform.h; do
    [ -f "$include/vulkan/$f" ] || curl -sSfL -o "$include/vulkan/$f" "$vk/vulkan/$f"
done
for f in $(grep -ho 'vk_video/[a-z0-9_]*\.h' "$include/vulkan/vulkan_core.h" | sort -u); do
    [ -f "$include/$f" ] || curl -sSfL -o "$include/$f" "$vk/$f"
done
for f in "$include"/vk_video/*.h; do
    for g in $(grep -ho '"vulkan_video_[a-z0-9_]*\.h"' "$f" | tr -d '"' | sort -u); do
        [ -f "$include/vk_video/$g" ] || curl -sSfL -o "$include/vk_video/$g" "$vk/vk_video/$g"
    done
done

if [ ! -x "$FJ_MPV_WORK/venv/bin/meson" ]; then
    python3 -m venv "$FJ_MPV_WORK/venv"
    "$FJ_MPV_WORK/venv/bin/pip" -q install meson ninja jinja2
fi
