@echo off
setlocal

set "PROJECT_ROOT=%~dp0..\.."
cd /d "%PROJECT_ROOT%"

if not exist "logs" mkdir "logs"

cd /d "%PROJECT_ROOT%\frontend"

echo [%date% %time%] Starting Medora frontend...
echo [%date% %time%] Starting Medora frontend...>> "%PROJECT_ROOT%\logs\frontend-dev.log"
call npm run dev >> "%PROJECT_ROOT%\logs\frontend-dev.log" 2>> "%PROJECT_ROOT%\logs\frontend-dev.err.log"
