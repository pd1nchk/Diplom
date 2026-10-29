@echo off
setlocal
cd /d "%~dp0"

echo.
echo === Stop Ktor API (port 8080) ===
set FOUND=0
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":8080" ^| findstr "LISTENING"') do (
  set FOUND=1
  echo Killing PID %%a ...
  taskkill /PID %%a /F >nul 2>&1
)

if "%FOUND%"=="0" (
  echo Port 8080 is free.
) else (
  timeout /t 2 /nobreak >nul
)

if exist "server\data\diplom.lock.db" (
  del /f "server\data\diplom.lock.db" 2>nul
  echo Removed stale H2 lock file: server\data\diplom.lock.db
)

netstat -ano 2>nul | findstr ":8080" | findstr "LISTENING" >nul
if %errorlevel%==0 (
  echo.
  echo WARNING: port 8080 still in use. Close the other server window or end java.exe in Task Manager.
) else (
  echo Port 8080 is free. Run: .\gradlew.bat :server:run
)
echo.
endlocal
