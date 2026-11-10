@echo off
start "" "%~dp0build\Release\FetchJet.exe" --bridge-port 28459 --no-auto-downloads %*
