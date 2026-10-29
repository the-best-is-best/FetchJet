#!/bin/bash
# FetchJet iOS development build. Double-click in Finder (opens Terminal).
# Writes the full log to build/ios-dev-build.log and a short summary of errors to
# build/ios-dev-build.errors.txt.
set -o pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1
mkdir -p build
LOG="$ROOT/build/ios-dev-build.log"
ERR="$ROOT/build/ios-dev-build.errors.txt"
: > "$LOG"; : > "$ERR"
STEP="${1:-all}"   # gradle | xcode | all

if [ -z "$JAVA_HOME" ]; then
  for cand in "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" \
              "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
              "$(/usr/libexec/java_home 2>/dev/null)"; do
    if [ -n "$cand" ] && [ -d "$cand" ]; then export JAVA_HOME="$cand"; break; fi
  done
fi
echo "JAVA_HOME=$JAVA_HOME" | tee -a "$LOG"
xcodebuild -version 2>&1 | tee -a "$LOG"

status=0
if [ "$STEP" = "gradle" ] || [ "$STEP" = "all" ]; then
  echo "=== STEP 1: Kotlin/Native framework (iosSimulatorArm64) ===" | tee -a "$LOG"
  ./gradlew :sharedUI:linkDebugFrameworkIosSimulatorArm64 --console=plain --no-daemon 2>&1 | tee -a "$LOG"
  status=${PIPESTATUS[0]}
  echo "GRADLE_EXIT=$status" | tee -a "$LOG"
fi

if [ $status -eq 0 ] && { [ "$STEP" = "xcode" ] || [ "$STEP" = "all" ]; }; then
  echo "=== STEP 2: xcodebuild (iOS Simulator, Debug) ===" | tee -a "$LOG"
  xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
    -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath build/DerivedData \
    CODE_SIGNING_ALLOWED=NO build 2>&1 | tee -a "$LOG"
  status=${PIPESTATUS[0]}
  echo "XCODE_EXIT=$status" | tee -a "$LOG"
fi

grep -E "^e: |error:|\*\* BUILD FAILED|FAILED|What went wrong" "$LOG" | grep -v "warning:" | head -200 > "$ERR"
echo "=== DONE exit=$status (log: $LOG) ===" | tee -a "$LOG"
echo "DONE_MARKER exit=$status" >> "$LOG"
