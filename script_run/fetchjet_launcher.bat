@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
pushd "%SCRIPT_DIR%.."
set "ROOT_DIR=%CD%"
popd
set "TARGET_URL=%~1"

:: 1. FetchJet already running? hand the link to it (bridge on 127.0.0.1:28450)
if not "%TARGET_URL%"=="" (
    powershell.exe -NoProfile -Command "try { $u=[System.Uri]::EscapeDataString('%TARGET_URL%'); Invoke-WebRequest -Uri ('http://127.0.0.1:28450/download?url='+$u) -TimeoutSec 2 -UseBasicParsing | Out-Null; exit 0 } catch { exit 1 }"
    if not errorlevel 1 exit /b 0
)

:: 2. Not running: start the installed app (jpackage per-user install, then all-users),
::    then the local build, then the dev fallback.
set "EXE="
if exist "%LOCALAPPDATA%\FetchJet\FetchJet.exe"               set "EXE=%LOCALAPPDATA%\FetchJet\FetchJet.exe"
if exist "%LOCALAPPDATA%\Programs\FetchJet\FetchJet.exe"      set "EXE=%LOCALAPPDATA%\Programs\FetchJet\FetchJet.exe"
if exist "%ProgramFiles%\FetchJet\FetchJet.exe"               set "EXE=%ProgramFiles%\FetchJet\FetchJet.exe"
if exist "%ROOT_DIR%\desktopApp\build\compose\binaries\main\app\FetchJet\FetchJet.exe" set "EXE=%ROOT_DIR%\desktopApp\build\compose\binaries\main\app\FetchJet\FetchJet.exe"

if not "%EXE%"=="" (
    start "" "%EXE%" "%TARGET_URL%"
    exit /b 0
)

:: 3. Development fallback: run from sources
cd /d "%ROOT_DIR%"
if not "%TARGET_URL%"=="" (
    start "" "gradlew.bat" :desktopApp:run --args="%TARGET_URL%"
) else (
    start "" "gradlew.bat" :desktopApp:run
)
exit /b 0
