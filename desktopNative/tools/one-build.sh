#!/usr/bin/env bash
if [ "$#" -eq 0 ]; then
    echo "usage: one-build.sh <command> [arguments]   (runs it when no other build of this folder is running)"
    exit 2
fi
lock="${FJ_BUILD_LOCK:-/e/tools/probes/build.lock}"
mkdir -p "$(dirname "$lock")"
waited=0
while ! mkdir "$lock" 2>/dev/null; do
    if [ -n "$(find "$lock" -maxdepth 0 -mmin +45 2>/dev/null)" ]; then
        rmdir "$lock" 2>/dev/null
        continue
    fi
    if [ "$waited" -eq 0 ]; then
        echo "one-build: another build is running, waiting"
    fi
    waited=$((waited + 5))
    sleep 5
done
trap 'rmdir "$lock" 2>/dev/null' EXIT
"$@"
