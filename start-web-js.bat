@echo off
cd /d "%~dp0"

call stop-web.bat
echo.
echo === Web client (Kotlin/JS), port 8081 ===
echo API must run first: start-server.bat in another window.
echo Browser: http://localhost:8081
echo.
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
