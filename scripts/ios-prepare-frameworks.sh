#!/bin/bash
# Xcode build phase "Prepare native frameworks" (runs before everything else, idempotent).
#  1. iosApp/Frameworks/Python.xcframework  - downloaded from BeeWare Python-Apple-support
#  2. iosApp/Frameworks/LibTorrent.xcframework - built from source by scripts/build-libtorrent-ios.command
# Both are git-ignored; this phase makes a fresh clone / CI machine self-sufficient.
set -o pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FW="$ROOT/iosApp/Frameworks"
mkdir -p "$FW" "$ROOT/build"

PY_VERSION="${FETCHJET_PYTHON_VERSION:-3.13}"
PY_BUILD="${FETCHJET_PYTHON_BUILD:-b14}"

# ---- 1. Python runtime -------------------------------------------------------------------
if [ ! -f "$FW/Python.xcframework/build/utils.sh" ]; then
  echo "note: Python.xcframework missing - downloading Python-$PY_VERSION-iOS-support.$PY_BUILD"
  URL="https://github.com/beeware/Python-Apple-support/releases/download/$PY_VERSION-$PY_BUILD/Python-$PY_VERSION-iOS-support.$PY_BUILD.tar.gz"
  TMP="$ROOT/build/python-apple-support"
  rm -rf "$TMP"; mkdir -p "$TMP"
  if ! curl -fL --retry 3 -o "$TMP/py.tgz" "$URL"; then
    echo "error: could not download $URL - put Python.xcframework into iosApp/Frameworks/ manually (see CLAUDE_HANDOFF.md)."
    exit 1
  fi
  tar -xzf "$TMP/py.tgz" -C "$TMP" Python.xcframework VERSIONS || { echo "error: could not extract Python runtime"; exit 1; }
  rm -rf "$FW/Python.xcframework"
  mv "$TMP/Python.xcframework" "$FW/Python.xcframework"
  cp "$TMP/VERSIONS" "$FW/PYTHON-VERSIONS.txt" 2>/dev/null
  rm -rf "$TMP"
  echo "note: installed $(head -1 "$FW/PYTHON-VERSIONS.txt" 2>/dev/null)"
fi

# ---- 2. libtorrent ------------------------------------------------------------------------
if [ ! -d "$FW/LibTorrent.xcframework" ]; then
  echo "note: LibTorrent.xcframework missing - building it (first time takes ~20 minutes)"
  BREW_BIN="$( [ -x /opt/homebrew/bin/brew ] && echo /opt/homebrew/bin || echo /usr/local/bin )"
  # Nested xcodebuild must not inherit this build's environment (SDKROOT, PLATFORM_NAME, ...).
  env -i HOME="$HOME" USER="$USER" LANG=en_US.UTF-8 \
      PATH="$BREW_BIN:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin" \
      DEVELOPER_DIR="${DEVELOPER_DIR:-$(xcode-select -p 2>/dev/null || echo /Applications/Xcode.app/Contents/Developer)}" \
      bash "$ROOT/scripts/build-libtorrent-ios.command"
  status=$?
  if [ $status -ne 0 ] || [ ! -d "$FW/LibTorrent.xcframework" ]; then
    echo "error: LibTorrent.xcframework build failed (exit $status). See build/libtorrent-ios.log. Prerequisites: brew install cmake boost"
    exit 1
  fi
fi
# ---- 3. curl_cffi for yt-dlp (browser impersonation) --------------------------------------
# Prebuilt iOS payload from tfourj/SwiftCurlCffi-iOS (what Palladium ships). Sites whose yt-dlp
# extractor requests impersonation (PornHub, many Cloudflare-fronted hosts) fail with HTTP 403 /
# "Unable to download webpage" without it. Optional: the build still succeeds when the download
# fails, yt-dlp just runs without impersonation.
CC_DIR="$FW/SwiftCurlCffi-iOS"
CC_PAYLOAD="$CC_DIR/Sources/SwiftCurlCffiIOS/Resources/curl_cffi_ios_payload.zip"
if [ ! -f "$CC_PAYLOAD" ]; then
  echo "note: curl_cffi payload missing - downloading SwiftCurlCffi-iOS.zip (latest release)"
  # Pinned, not "latest": the bundled yt-dlp (2026.10.21) only accepts curl_cffi 0.10.x - 0.16.x and
  # v0.17.0 of the payload ships curl_cffi 0.17. Bump both together.
  CC_TAG="${FETCHJET_CURL_CFFI_TAG:-v0.16.3}"
  CC_URL="${FETCHJET_CURL_CFFI_URL:-https://github.com/tfourj/SwiftCurlCffi-iOS/releases/download/$CC_TAG/SwiftCurlCffi-iOS.zip}"
  TMP="$ROOT/build/swiftcurlcffi"
  rm -rf "$TMP"; mkdir -p "$TMP"
  if curl -fL --retry 3 -o "$TMP/cc.zip" "$CC_URL" && unzip -q -o "$TMP/cc.zip" -d "$TMP"; then
    if [ -f "$TMP/SwiftCurlCffi-iOS/Sources/SwiftCurlCffiIOS/Resources/curl_cffi_ios_payload.zip" ]; then
      rm -rf "$CC_DIR"
      mv "$TMP/SwiftCurlCffi-iOS" "$CC_DIR"
    else
      echo "warning: SwiftCurlCffi-iOS.zip has no curl_cffi_ios_payload.zip inside - skipping curl_cffi"
    fi
  else
    echo "warning: could not download $CC_URL - yt-dlp will run without impersonation (curl_cffi)."
    echo "warning: to build it yourself: git clone https://github.com/tfourj/SwiftCurlCffi-iOS $CC_DIR && PYTHON_XCFRAMEWORK=$FW/Python.xcframework $CC_DIR/scripts/build-all.sh  (needs brew install cmake ninja, host python$PY_VERSION)"
  fi
  rm -rf "$TMP"
fi
if [ -f "$CC_PAYLOAD" ]; then
  CC_PY=$(sed -n 's/.*"python": *"\([0-9.]*\)".*/\1/p' "$CC_DIR/Sources/SwiftCurlCffiIOS/Resources/manifest.json" 2>/dev/null)
  if [ -n "$CC_PY" ] && [ "$CC_PY" != "$PY_VERSION" ]; then
    echo "warning: curl_cffi payload was built for Python $CC_PY but the app embeds Python $PY_VERSION - it will be skipped. Rebuild it with scripts/build-all.sh in $CC_DIR (PYTHON_XCFRAMEWORK=$FW/Python.xcframework) or set FETCHJET_CURL_CFFI_URL to a matching release."
  else
    echo "note: curl_cffi payload ready ($(sed -n 's/.*"curl_cffi": *"\([^"]*\)".*/\1/p' "$CC_DIR/Sources/SwiftCurlCffiIOS/Resources/manifest.json" 2>/dev/null))"
  fi
fi
echo "Native frameworks ready: $(ls "$FW" | tr '\n' ' ')"
