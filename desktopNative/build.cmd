@echo off
call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat" >nul
cd /d %~dp0
cmake -S . -B build -G "Visual Studio 18 2026" -A x64 > build.log 2>&1
cmake --build build --config Release >> build.log 2>&1
echo EXIT %ERRORLEVEL% >> build.log
