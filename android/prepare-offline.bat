@echo off
REM ===================================================================
REM  Run ONCE while connected to the internet.
REM  Downloads Gradle 8.2 + every Android/Kotlin library this project
REM  needs into .gradle-offline\ INSIDE the project folder.
REM  After that, build-offline.bat works with no internet at all
REM  (you can even copy the whole folder to another PC).
REM ===================================================================
setlocal
cd /d "%~dp0"

if not "%JAVA_HOME%"=="" (
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
  )
)

set "GRADLE_USER_HOME=%~dp0.gradle-offline"
echo Using GRADLE_USER_HOME=%GRADLE_USER_HOME%

call gradlew.bat --no-daemon assembleDebug
if errorlevel 1 (
  echo.
  echo *** Preparation FAILED - check the error above and your internet connection. ***
  exit /b 1
)

echo.
echo ==== Done. Everything needed is cached in .gradle-offline ====
echo ==== From now on run build-offline.bat (no internet needed) ====
exit /b 0
