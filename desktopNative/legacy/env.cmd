@echo off
if "%FJ_LEGACY_TOOLS%"=="" set "FJ_LEGACY_TOOLS=E:\tools\legacy"
if "%FJ_LEGACY_MSVC%"=="" set "FJ_LEGACY_MSVC=%FJ_LEGACY_TOOLS%\msvc\VC\Tools\MSVC\14.44.35207"
if "%FJ_LEGACY_REDIST%"=="" set "FJ_LEGACY_REDIST=%FJ_LEGACY_TOOLS%\msvc\VC\Redist\MSVC\14.44.35112\x64\Microsoft.VC143.CRT"
if "%FJ_WINSDK%"=="" set "FJ_WINSDK=C:\Program Files (x86)\Windows Kits\10"
if "%FJ_WINSDK_VER%"=="" set "FJ_WINSDK_VER=10.0.26100.0"
set "FJ_VS_CMAKE=C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake"
if not exist "%FJ_LEGACY_MSVC%\bin\Hostx64\x64\cl.exe" (
    echo legacy env: MSVC 14.44 not found at %FJ_LEGACY_MSVC%
    exit /b 1
)
set "RUSTUP_HOME=%FJ_LEGACY_TOOLS%\rustup"
set "CARGO_HOME=%FJ_LEGACY_TOOLS%\cargo"
set "VCToolsInstallDir=%FJ_LEGACY_MSVC%\"
set "VCToolsRedistDir=%FJ_LEGACY_TOOLS%\msvc\VC\Redist\MSVC\14.44.35112\"
set "WindowsSdkDir=%FJ_WINSDK%\"
set "WindowsSDKVersion=%FJ_WINSDK_VER%\"
set "UCRTVersion=%FJ_WINSDK_VER%"
set "UniversalCRTSdkDir=%FJ_WINSDK%\"
set "VSCMD_ARG_TGT_ARCH=x64"
set "Platform=x64"
set "INCLUDE=%FJ_LEGACY_MSVC%\include;%FJ_WINSDK%\Include\%FJ_WINSDK_VER%\ucrt;%FJ_WINSDK%\Include\%FJ_WINSDK_VER%\shared;%FJ_WINSDK%\Include\%FJ_WINSDK_VER%\um;%FJ_WINSDK%\Include\%FJ_WINSDK_VER%\winrt;%FJ_WINSDK%\Include\%FJ_WINSDK_VER%\cppwinrt"
set "LIB=%FJ_LEGACY_MSVC%\lib\x64;%FJ_WINSDK%\Lib\%FJ_WINSDK_VER%\ucrt\x64;%FJ_WINSDK%\Lib\%FJ_WINSDK_VER%\um\x64"
set "LIBPATH=%FJ_LEGACY_MSVC%\lib\x64"
set "PATH=%FJ_LEGACY_MSVC%\bin\Hostx64\x64;%FJ_WINSDK%\bin\%FJ_WINSDK_VER%\x64;%FJ_VS_CMAKE%\CMake\bin;%FJ_VS_CMAKE%\Ninja;%CARGO_HOME%\bin;%PATH%"
exit /b 0
