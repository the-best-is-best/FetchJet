#!/usr/bin/env bash
cd "$(dirname "$0")"
mkdir -p build
g++ -O1 -std=c++17 fake_ytdlp.cpp -o build/yt-dlp_linux > build/build-linux.log 2>&1
echo "EXIT $?" >> build/build-linux.log
