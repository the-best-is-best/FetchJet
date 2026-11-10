@echo off
call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat" >nul
cd /d %~dp0
if not exist build mkdir build
cl /nologo /O1 /EHsc /std:c++20 /utf-8 fake_ytdlp.cpp /Fe:build\yt-dlp.exe /Fo:build\ > build\build.log 2>&1
echo EXIT %ERRORLEVEL% >> build\build.log
