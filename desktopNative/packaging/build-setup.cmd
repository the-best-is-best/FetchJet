@echo off
setlocal
set "HERE=%~dp0"
for %%i in ("%~dp0..\..") do set "REPO=%%~fi"
if not defined FJ_OUT set "FJ_OUT=E:\tools\probes\package\out"
set "OBJ=%FJ_OUT%\wixobj-setup"
set "LOG=%FJ_OUT%\build-setup.log"

set "WIXBIN="
if defined WIX if exist "%WIX%bin\candle.exe" set "WIXBIN=%WIX%bin"
if not defined WIXBIN if exist "C:\Program Files (x86)\WiX Toolset v3.14\bin\candle.exe" set "WIXBIN=C:\Program Files (x86)\WiX Toolset v3.14\bin"
if not defined WIXBIN for /f "delims=" %%i in ('where candle.exe 2^>nul') do set "WIXBIN=%%~dpi"
if not defined WIXBIN (
    echo build-setup: FAILED: WiX 3 not found ^(candle.exe^). Install WiX Toolset 3.14 or set WIX.
    exit /b 1
)
if "%WIXBIN:~-1%"=="\" set "WIXBIN=%WIXBIN:~0,-1%"

set "VER="
for /f "usebackq delims=" %%v in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%HERE%version.ps1"`) do set "VER=%%v"
if not defined VER (
    echo build-setup: FAILED: could not read APP_VERSION
    exit /b 1
)

set "MSI=%FJ_OUT%\FetchJet-%VER%.msi"
if "%FJ_LEGACY%"=="1" set "MSI=%FJ_OUT%\FetchJet-%VER%-Windows-7-8-8.1.msi"
if not exist "%MSI%" (
    echo build-setup: FAILED: no %MSI%, run packaging\build-msi.cmd first
    exit /b 1
)

if not exist "%OBJ%" mkdir "%OBJ%"
del /q "%OBJ%\*.wixobj" 2>nul
set "SETUP=%FJ_OUT%\FetchJet-%VER%-setup.exe"
if "%FJ_LEGACY%"=="1" set "SETUP=%FJ_OUT%\FetchJet-%VER%-Setup-Windows-7-8-8.1.exe"
if exist "%SETUP%" del /q "%SETUP%"

echo START %DATE% %TIME% version %VER% msi %MSI% wix %WIXBIN% > "%LOG%"
start "" /low /b /wait "%WIXBIN%\candle.exe" -nologo -arch x64 -ext WixBalExtension -dVersion=%VER% -dMsi="%MSI%" -dIcon="%REPO%\desktopNative\res\fetchjet.ico" -dArt="%HERE%installer" -out "%OBJ%\\" "%HERE%bundle.wxs" >> "%LOG%" 2>&1
if errorlevel 1 goto failed

start "" /low /b /wait "%WIXBIN%\light.exe" -nologo -ext WixBalExtension -spdb -out "%SETUP%" "%OBJ%\bundle.wixobj" >> "%LOG%" 2>&1
if errorlevel 1 goto failed
if not exist "%SETUP%" goto failed

echo END %DATE% %TIME% >> "%LOG%"
echo EXIT 0 >> "%LOG%"
echo build-setup: ok, %SETUP%
exit /b 0

:failed
echo END %DATE% %TIME% >> "%LOG%"
echo EXIT 1 >> "%LOG%"
echo build-setup: FAILED, see %LOG%
exit /b 1
