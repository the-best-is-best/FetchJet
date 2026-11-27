@echo off
setlocal
call "%~dp0env.cmd" || exit /b 1
set "QJS_VERSION=0.18.0"
set "QJS_SHA256=28bcd89aa3683fd2a946537e5ce3f11cc46b5cbf3383c7561aaef3df5ff9f1dd"
set "QJS_DIR=%FJ_LEGACY_TOOLS%\src\qjs-src"
set "QJS_TAR=%QJS_DIR%\quickjs-ng-%QJS_VERSION%.tar.gz"
set "QJS_SRC=%QJS_DIR%\quickjs-%QJS_VERSION%"
set "QJS_OUT=%FJ_LEGACY_TOOLS%\qjs-build"
if not exist "%QJS_DIR%" mkdir "%QJS_DIR%"
if not exist "%QJS_TAR%" curl.exe -s -L --fail -o "%QJS_TAR%" "https://github.com/quickjs-ng/quickjs/archive/refs/tags/v%QJS_VERSION%.tar.gz" || exit /b 1
for /f %%h in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%QJS_TAR%').Hash.ToLower()"') do set "QJS_GOT=%%h"
if not "%QJS_GOT%"=="%QJS_SHA256%" (
    echo build-qjs: FAILED: source checksum %QJS_GOT% is not %QJS_SHA256%
    exit /b 1
)
if not exist "%QJS_SRC%\CMakeLists.txt" tar -xzf "%QJS_TAR%" -C "%QJS_DIR%" || exit /b 1
cmake -S "%QJS_SRC%" -B "%QJS_OUT%" -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=cl ^
 -DCMAKE_POLICY_DEFAULT_CMP0091=NEW -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded ^
 -DCMAKE_C_FLAGS="/D_WIN32_WINNT=0x0601 /DWINVER=0x0601" > "%FJ_LEGACY_TOOLS%\build-qjs.log" 2>&1 || goto failed
cmake --build "%QJS_OUT%" --target qjs_exe -j 4 >> "%FJ_LEGACY_TOOLS%\build-qjs.log" 2>&1 || goto failed
if not exist "%QJS_OUT%\qjs.exe" goto failed
echo build-qjs: ok, %QJS_OUT%\qjs.exe
exit /b 0
:failed
echo build-qjs: FAILED, see %FJ_LEGACY_TOOLS%\build-qjs.log
exit /b 1
