@echo off
setlocal
set PATH=C:\msys64\clang64\bin;%PATH%
if "%FJT_BUILD_DIR%"=="" set FJT_BUILD_DIR=E:\tools\probes\fjtorrent\build
if "%LIBTORRENT_SOURCE_DIR%"=="" set LIBTORRENT_SOURCE_DIR=E:/tools/libtorrent-2.1.2
cmake -S "%~dp0." -B "%FJT_BUILD_DIR%" -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ -DCMAKE_CXX_STANDARD=17 -DLIBTORRENT_SOURCE_DIR=%LIBTORRENT_SOURCE_DIR% -DBUILD_SHARED_LIBS=OFF -DOPENSSL_USE_STATIC_LIBS=ON -Ddeprecated-functions=OFF -Dwebtorrent=OFF -Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF
if errorlevel 1 exit /b 1
cmake --build "%FJT_BUILD_DIR%" -j 4 %*
if errorlevel 1 exit /b 1
exit /b 0
