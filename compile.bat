@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not exist "build" mkdir build
if not exist "lib\mysql-connector-j-9.1.0.jar" (
    echo [ERROR] Missing lib\mysql-connector-j-9.1.0.jar
    exit /b 1
)

echo Compiling CineVault sources...
dir /s /b *.java > sources.tmp
javac -encoding UTF-8 -cp "lib\mysql-connector-j-9.1.0.jar" -d build @sources.tmp
set ERR=%ERRORLEVEL%
del sources.tmp

if %ERR% neq 0 (
    echo [FAILED] Compilation failed with exit code %ERR%.
    exit /b %ERR%
)

echo [OK] Build succeeded in build\
endlocal
