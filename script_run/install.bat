@echo off
title FetchJet - Browser extension setup
color 0B
echo ====================================================================
echo      FetchJet - Browser extension and fetchjet:// protocol setup
echo ====================================================================
echo.

pushd "%~dp0"
set "SCRIPT_DIR=%CD%"
popd
pushd "%SCRIPT_DIR%\..\extension"
set "EXT_DIR=%CD%"
popd
set "LAUNCHER_BAT=%SCRIPT_DIR%\fetchjet_launcher.bat"

echo [1] Registering the fetchjet:// and kortex:// URL protocols (current user)...
for %%P in (fetchjet kortex) do (
    reg add "HKCU\Software\Classes\%%P" /ve /t REG_SZ /d "URL:FetchJet Protocol" /f >nul
    reg add "HKCU\Software\Classes\%%P" /v "URL Protocol" /t REG_SZ /d "" /f >nul
    reg add "HKCU\Software\Classes\%%P\shell\open\command" /ve /t REG_SZ /d "\"%LAUNCHER_BAT%\" \"%%1\"" /f >nul
)
if %ERRORLEVEL% EQU 0 (
    echo     [OK] Protocols registered.
) else (
    echo     [WARN] Could not register the protocols.
)

echo.
echo [2] Extension folder copied to the clipboard:
echo     %EXT_DIR%
<nul set /p="%EXT_DIR%"|clip
echo.

echo [3] Opening the Chrome / Edge extensions pages...
start "" "chrome://extensions" 2>nul
start "" "edge://extensions" 2>nul

echo.
echo ====================================================================
echo   To finish in your browser:
echo   1. Turn ON "Developer mode" (top right).
echo   2. Click "Load unpacked".
echo   3. Press Ctrl+V to paste the extension path, then Enter.
echo ====================================================================
echo.
pause
