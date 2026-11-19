@echo off
cd /d "%~dp0"
call compile.bat
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
echo Launching CineVault...
java -cp "build;lib\mysql-connector-j-9.1.0.jar" Main
