@echo off
setlocal
cd /d "%~dp0.."
if not exist gradlew.bat (
  echo Gradle wrapper not found. Running the project bootstrap first...
  call bootstrap-gradle.bat
)
call gradlew.bat runDevServer
endlocal
