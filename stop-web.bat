@echo off
setlocal
cd /d "%~dp0"

echo.
echo === Stop web dev-server (ports 8081, 8082) ===
set FOUND=0
for %%P in (8081 8082) do (
  for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":%%P" ^| findstr "LISTENING"') do (
    set FOUND=1
    echo Killing PID %%a on port %%P ...
    taskkill /PID %%a /F >nul 2>&1
  )
)

if "%FOUND%"=="0" (
  echo Ports 8081 and 8082 are free.
) else (
  timeout /t 2 /nobreak >nul
)

netstat -ano 2>nul | findstr ":8082" | findstr "LISTENING" >nul
if %errorlevel%==0 (
  echo WARNING: port 8082 still in use. Close the old Gradle web window.
) else (
  echo Port 8082 is free. Run: start-web.bat
)
echo.
endlocal
