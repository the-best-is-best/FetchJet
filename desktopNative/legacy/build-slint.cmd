@echo off
setlocal
call "%~dp0env.cmd" || exit /b 1
if "%FJ_SLINT_SRC%"=="" set "FJ_SLINT_SRC=%FJ_LEGACY_TOOLS%\src\slint-1.18.1"
if "%FJ_SLINT_BUILD%"=="" set "FJ_SLINT_BUILD=%FJ_LEGACY_TOOLS%\slint-build"
if "%FJ_SLINT_OUT%"=="" set "FJ_SLINT_OUT=%FJ_LEGACY_TOOLS%\slint"
if "%FJ_SLINT_COMPILER%"=="" set "FJ_SLINT_COMPILER=E:\tools\slint\bin\slint-compiler.exe"
if "%FJ_CORROSION_SRC%"=="" set "FJ_CORROSION_SRC=%FJ_LEGACY_TOOLS%\src\corrosion-0.6.1"
if not exist "%FJ_CORROSION_SRC%\cmake\FindRust.cmake" (
    echo build-slint: FAILED: no Corrosion source in %FJ_CORROSION_SRC% ^(git clone --branch v0.6.1 https://github.com/corrosion-rs/corrosion.git^)
    exit /b 1
)
python -I "%~dp0patch-corrosion.py" "%FJ_CORROSION_SRC%" || exit /b 1
if not exist "%FJ_SLINT_SRC%\.cargo" mkdir "%FJ_SLINT_SRC%\.cargo"
copy /y "%~dp0slint-cargo-config.toml" "%FJ_SLINT_SRC%\.cargo\config.toml" >nul || exit /b 1
pushd "%FJ_SLINT_SRC%"
cargo fetch --quiet || (popd & exit /b 1)
popd
python -I "%~dp0patch-slint-win7.py" "%FJ_SLINT_SRC%" "%CARGO_HOME%\registry\src\index.crates.io-1949cf8c6b5b557f" "%FJ_LEGACY_TOOLS%\src\patched" || exit /b 1
set "LOG=%FJ_LEGACY_TOOLS%\build-slint.log"
echo START %DATE% %TIME% > "%LOG%"
cmd /c start /low /b /wait cmake -S "%FJ_SLINT_SRC%\api\cpp" -B "%FJ_SLINT_BUILD%" -G Ninja -DCMAKE_BUILD_TYPE=Release ^
 -DCMAKE_C_COMPILER=cl -DCMAKE_CXX_COMPILER=cl ^
 -DRust_TOOLCHAIN=nightly -DRust_CARGO_TARGET=x86_64-win7-windows-msvc ^
 -DSLINT_COMPILER="%FJ_SLINT_COMPILER:\=/%" ^
 -DSLINT_FEATURE_RENDERER_FEMTOVG=OFF -DSLINT_FEATURE_RENDERER_FEMTOVG_WGPU=OFF -DSLINT_FEATURE_RENDERER_SKIA=OFF ^
 -DSLINT_FEATURE_RENDERER_SOFTWARE=ON -DSLINT_FEATURE_BACKEND_WINIT=ON ^
 -DSLINT_FEATURE_INTERPRETER=OFF -DSLINT_FEATURE_LIVE_PREVIEW=OFF ^
 -DFETCHCONTENT_SOURCE_DIR_CORROSION="%FJ_CORROSION_SRC%" ^
 -DCMAKE_INSTALL_PREFIX="%FJ_SLINT_OUT%" >> "%LOG%" 2>&1
if errorlevel 1 goto failed
if not exist "%FJ_SLINT_BUILD%\build.ninja" goto failed
cmd /c start /low /b /wait cmake --build "%FJ_SLINT_BUILD%" -j 4 >> "%LOG%" 2>&1
if errorlevel 1 goto failed
cmd /c start /low /b /wait cmake --install "%FJ_SLINT_BUILD%" >> "%LOG%" 2>&1
if errorlevel 1 goto failed
if not exist "%FJ_SLINT_OUT%\lib\slint_cpp.dll" if not exist "%FJ_SLINT_OUT%\bin\slint_cpp.dll" goto failed
if not exist "%FJ_SLINT_OUT%\bin" mkdir "%FJ_SLINT_OUT%\bin"
copy /y "%FJ_SLINT_COMPILER%" "%FJ_SLINT_OUT%\bin\slint-compiler.exe" >nul
echo EXIT 0 >> "%LOG%"
echo build-slint: ok, %FJ_SLINT_OUT%
exit /b 0
:failed
echo EXIT 1 >> "%LOG%"
echo build-slint: FAILED, see %LOG%
exit /b 1
