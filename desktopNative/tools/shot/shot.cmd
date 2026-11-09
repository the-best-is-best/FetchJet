@echo off
setlocal
set SHOT_EXE=%~dp0build\Release\shot.exe
if not exist "%SHOT_EXE%" (
  echo shot.exe is not built yet. Run "%~dp0build.cmd" once, then try again. 1>&2
  exit /b 9
)
"%SHOT_EXE%" %*
exit /b %ERRORLEVEL%
