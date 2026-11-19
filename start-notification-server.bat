@echo off
cd /d "%~dp0"
if not exist "build" call compile.bat
echo Starting CineVault Real-Time TCP Notification Server...
java -cp "build;lib\mysql-connector-j-9.1.0.jar" net.NotificationServer
