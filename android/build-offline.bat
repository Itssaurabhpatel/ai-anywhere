@echo off
REM Builds the debug APK with NO internet, using the cache made by prepare-offline.bat.
REM Output: app\build\outputs\apk\debug\app-debug.apk
setlocal
cd /d "%~dp0"

if not "%JAVA_HOME%"=="" (
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
  )
)

if not exist "%~dp0.gradle-offline\wrapper\dists" (
  echo *** Run prepare-offline.bat once with internet first. ***
  exit /b 1
)

set "GRADLE_USER_HOME=%~dp0.gradle-offline"
call gradlew.bat --offline assembleDebug %*
if errorlevel 1 exit /b 1
echo.
echo ========================================================
echo BUILD SUCCESSFUL (OFFLINE MODE)
echo APK Generated: app\build\outputs\apk\debug\app-debug.apk
echo ========================================================
