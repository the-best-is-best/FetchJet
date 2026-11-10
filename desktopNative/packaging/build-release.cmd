@echo off
setlocal
call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
cd /d "%~dp0.."
if not exist build-release mkdir build-release
if not exist build-release\.gitignore (echo *> build-release\.gitignore)
set LOG=build-release\build.log
echo START %DATE% %TIME% > "%LOG%"
cmd /c start /low /b /wait cmake -S . -B build-release -G "Visual Studio 18 2026" -A x64 -DFJ_CORE_KIND=releaseShared >> "%LOG%" 2>&1
if errorlevel 1 goto failed
cmd /c start /low /b /wait cmake --build build-release --config Release -- /nodeReuse:false /lowPriority:true >> "%LOG%" 2>&1
if errorlevel 1 goto failed
echo END %DATE% %TIME% >> "%LOG%"
echo EXIT 0 >> "%LOG%"
echo build-release: ok, see %CD%\build-release\Release
exit /b 0
:failed
echo END %DATE% %TIME% >> "%LOG%"
echo EXIT 1 >> "%LOG%"
echo build-release: FAILED, see %CD%\%LOG%
exit /b 1
