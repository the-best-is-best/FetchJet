@echo off
setlocal
call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat" >nul
cd /d %~dp0
set OUT=%~1
if "%OUT%"=="" set OUT=build
cmake -S . -B "%OUT%" -G "Visual Studio 18 2026" -A x64 > "%OUT%.log" 2>&1
cmake --build "%OUT%" --config Release >> "%OUT%.log" 2>&1
echo EXIT %ERRORLEVEL% >> "%OUT%.log"
endlocal
