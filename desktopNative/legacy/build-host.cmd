@echo off
setlocal
call "%~dp0env.cmd" || exit /b 1
if "%FJ_LEGACY_BUILD%"=="" set "FJ_LEGACY_BUILD=%FJ_LEGACY_TOOLS%\host-build"
if "%FJ_LEGACY_TORRENT%"=="" set "FJ_LEGACY_TORRENT=%FJ_LEGACY_TOOLS%\fjtorrent-build\fjtorrent.dll"
set "LOG=%FJ_LEGACY_TOOLS%\build-host.log"
echo START %DATE% %TIME% > "%LOG%"
cmd /c start /low /b /wait cmake -S "%~dp0.." -B "%FJ_LEGACY_BUILD%" -G Ninja -DCMAKE_BUILD_TYPE=Release ^
 -DCMAKE_C_COMPILER=cl -DCMAKE_CXX_COMPILER=cl -DFJ_LEGACY=ON ^
 -DSLINT_DIR="%FJ_LEGACY_TOOLS%\slint" -DFJ_TORRENT_DLL="%FJ_LEGACY_TORRENT%" -DFJ_CORE_KIND=releaseShared >> "%LOG%" 2>&1
if not exist "%FJ_LEGACY_BUILD%\build.ninja" goto failed
cmd /c start /low /b /wait cmake --build "%FJ_LEGACY_BUILD%" -j 4 >> "%LOG%" 2>&1
if not exist "%FJ_LEGACY_BUILD%\FetchJet.exe" goto failed
echo EXIT 0 >> "%LOG%"
echo build-host: ok, %FJ_LEGACY_BUILD%
exit /b 0
:failed
echo EXIT 1 >> "%LOG%"
echo build-host: FAILED, see %LOG%
exit /b 1
