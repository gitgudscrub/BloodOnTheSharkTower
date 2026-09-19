@echo off
setlocal
cd /d "%~dp0.."
call gradlew.bat runPlayer2
endlocal
