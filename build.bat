@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "GV=9.1.0"
set "GDIR=%~dp0.gradle-dist"

echo === GunsPlugin build ===

where java >nul 2>&1
if errorlevel 1 goto :nojava

set "JV="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do if not defined JV set "JV=%%~v"
for /f "delims=.-" %%a in ("%JV%") do set "JMAJOR=%%a"
echo Java version: %JV%
if %JMAJOR% LSS 25 goto :nojava

set "GRADLE_CMD="
where gradle >nul 2>&1 && set "GRADLE_CMD=gradle"
if defined GRADLE_CMD goto :build

if not exist "%GDIR%\gradle-%GV%\bin\gradle.bat" (
  echo Gradle not found, downloading Gradle %GV% ...
  if not exist "%GDIR%" mkdir "%GDIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GV%-bin.zip' -OutFile '%GDIR%\gradle.zip'"
  if errorlevel 1 goto :fail
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%GDIR%\gradle.zip' '%GDIR%'"
  if errorlevel 1 goto :fail
  del "%GDIR%\gradle.zip"
)
set "GRADLE_CMD=%GDIR%\gradle-%GV%\bin\gradle.bat"

:build
echo Building plugin ...
call "%GRADLE_CMD%" build --no-daemon
if errorlevel 1 goto :fail

if exist dist rmdir /s /q dist
mkdir dist
copy /y build\libs\*.jar dist\ >nul

echo Packing resource pack ...
tar -a -cf dist\GunPack.zip -C resourcepack .
if errorlevel 1 goto :fail
certutil -hashfile dist\GunPack.zip SHA1

echo.
echo DONE. Files are in the "dist" folder:
dir /b dist
echo.
echo - put the .jar into the server "plugins" folder
echo - GunPack.zip is the resource pack
pause
exit /b 0

:nojava
echo.
echo ERROR: JDK 25 or newer is required. Install it from https://adoptium.net
echo and run this file again.
pause
exit /b 1

:fail
echo.
echo BUILD FAILED. Copy the error text above and send it to Claude.
pause
exit /b 1
