@echo off
cd /d "%~dp0"

call stop-web.bat
echo.
echo === Web client (Wasm), port 8082 ===
echo API must run first: start-server.bat in another window.
echo Browser: http://localhost:8082  (Ctrl+F5 after start)
echo If white screen: try start-web-js.bat (port 8081)
echo.
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
