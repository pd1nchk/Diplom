@echo off
cd /d "%~dp0"

call stop-server.bat
echo.
echo === Start Ktor API ===
echo Wait for: Responding at http://127.0.0.1:8080
echo Keep this window open. Stop: Ctrl+C or stop-server.bat
echo.
.\gradlew.bat :server:run
