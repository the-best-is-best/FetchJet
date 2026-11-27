@echo off
setlocal
set "PATH=C:\msys64\clang64\bin;%PATH%"
if "%FJ_LEGACY_TOOLS%"=="" set "FJ_LEGACY_TOOLS=E:\tools\legacy"
if "%FJT_BUILD_DIR%"=="" set "FJT_BUILD_DIR=%FJ_LEGACY_TOOLS%\fjtorrent-build"
if "%LIBTORRENT_SOURCE_DIR%"=="" set "LIBTORRENT_SOURCE_DIR=E:/tools/libtorrent-2.1.2"
cmake -S "%~dp0..\torrent" -B "%FJT_BUILD_DIR%" -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ -DCMAKE_CXX_STANDARD=17 -DLIBTORRENT_SOURCE_DIR=%LIBTORRENT_SOURCE_DIR% -DBUILD_SHARED_LIBS=OFF -DOPENSSL_USE_STATIC_LIBS=ON -Ddeprecated-functions=OFF -Dwebtorrent=OFF -Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF -DFJT_LEGACY=ON || exit /b 1
cmake --build "%FJT_BUILD_DIR%" -j 4 --target fjtorrent || exit /b 1
if not exist "%FJT_BUILD_DIR%\fjtorrent.dll" exit /b 1
echo build-torrent: ok, %FJT_BUILD_DIR%\fjtorrent.dll
exit /b 0
