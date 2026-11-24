#!/bin/bash
# One self-contained libmpv.2.dylib for macOS arm64 (minimum macOS 12.0), gzipped as libmpv-macos-arm64-0.42.0.gz.
# Usage: FJ_MPV_WORK=<empty work folder> scripts/libmpv-macos/build-all.sh   (result in $FJ_MPV_WORK/dist)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
"$here/fetch-sources.sh"
"$here/build-deps.sh"
"$here/build-ffmpeg.sh"
"$here/build-mpv.sh"
