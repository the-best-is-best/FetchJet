@echo off
setlocal
set "HERE=%~dp0"
for %%i in ("%~dp0..\..") do set "REPO=%%~fi"
if not defined FJ_STAGE set "FJ_STAGE=E:\tools\probes\package\stage"
if not defined FJ_OUT set "FJ_OUT=E:\tools\probes\package\out"
set "OBJ=%FJ_OUT%\wixobj"
set "LOG=%FJ_OUT%\build-msi.log"

set "WIXBIN="
if defined WIX if exist "%WIX%bin\candle.exe" set "WIXBIN=%WIX%bin"
if not defined WIXBIN if exist "C:\Program Files (x86)\WiX Toolset v3.14\bin\candle.exe" set "WIXBIN=C:\Program Files (x86)\WiX Toolset v3.14\bin"
if not defined WIXBIN for /f "delims=" %%i in ('where candle.exe 2^>nul') do set "WIXBIN=%%~dpi"
if not defined WIXBIN (
    echo build-msi: FAILED: WiX 3 not found ^(candle.exe^). Install WiX Toolset 3.14 or set WIX.
    exit /b 1
)
if "%WIXBIN:~-1%"=="\" set "WIXBIN=%WIXBIN:~0,-1%"

set "VER="
for /f "usebackq delims=" %%v in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%HERE%version.ps1"`) do set "VER=%%v"
if not defined VER (
    echo build-msi: FAILED: could not read APP_VERSION
    exit /b 1
)

if not exist "%FJ_STAGE%\FetchJet.exe" (
    echo build-msi: FAILED: no stage in %FJ_STAGE%, run packaging\stage.ps1 first
    exit /b 1
)

if not exist "%OBJ%" mkdir "%OBJ%"
del /q "%OBJ%\*.wixobj" "%OBJ%\files.wxs" 2>nul
set "MSI=%FJ_OUT%\FetchJet-%VER%.msi"
if exist "%MSI%" del /q "%MSI%"

echo START %DATE% %TIME% version %VER% stage %FJ_STAGE% wix %WIXBIN% > "%LOG%"
powershell -NoProfile -ExecutionPolicy Bypass -File "%HERE%gen-files.ps1" -Stage "%FJ_STAGE%" -Out "%OBJ%\files.wxs" >> "%LOG%" 2>&1
if errorlevel 1 goto failed

start "" /low /b /wait "%WIXBIN%\candle.exe" -nologo -arch x64 -dStage="%FJ_STAGE%" -dVersion=%VER% -dIcon="%REPO%\desktopNative\res\fetchjet.ico" -out "%OBJ%\\" "%HERE%product.wxs" "%OBJ%\files.wxs" >> "%LOG%" 2>&1
if errorlevel 1 goto failed

start "" /low /b /wait "%WIXBIN%\light.exe" -nologo -ext WixUIExtension -cultures:en-us -sice:ICE91 -spdb -out "%MSI%" "%OBJ%\product.wixobj" "%OBJ%\files.wixobj" >> "%LOG%" 2>&1
if errorlevel 1 goto failed
if not exist "%MSI%" goto failed

echo END %DATE% %TIME% >> "%LOG%"
echo EXIT 0 >> "%LOG%"
echo build-msi: ok, %MSI%
exit /b 0

:failed
echo END %DATE% %TIME% >> "%LOG%"
echo EXIT 1 >> "%LOG%"
echo build-msi: FAILED, see %LOG%
exit /b 1
