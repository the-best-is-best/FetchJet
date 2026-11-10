@echo off
cd /d "%~dp0\.."
call "%~dp0build.cmd" debugShared
if errorlevel 1 exit /b 1
call "%~dp0run-dev.cmd"
