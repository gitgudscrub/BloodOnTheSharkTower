@echo off
setlocal
cd /d "%~dp0.."

if not exist gradlew.bat (
  echo Gradle wrapper not found. Running the project bootstrap first...
  call bootstrap-gradle.bat
)

start "Sharktower Dev Server" cmd /k "gradlew.bat runDevServer"
echo.
echo The local dev server is opening in a separate window.
echo Wait until that window says Done, then press any key here to launch the clients.
pause >nul
start "Sharktower Storyteller" cmd /k "gradlew.bat runStoryteller"
start "Sharktower Player 1" cmd /k "gradlew.bat runPlayer1"
start "Sharktower Player 2" cmd /k "gradlew.bat runPlayer2"
endlocal
