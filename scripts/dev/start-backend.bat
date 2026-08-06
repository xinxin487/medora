@echo off
setlocal

set "PROJECT_ROOT=%~dp0..\.."
cd /d "%PROJECT_ROOT%"

if not exist "logs" mkdir "logs"

echo [%date% %time%] Starting Medora backend...
echo [%date% %time%] Starting Medora backend...>> "%PROJECT_ROOT%\logs\backend-dev.log"
call "%MAVEN_HOME%\bin\mvn.cmd" -Dspring-boot.run.jvmArguments="-Dspringdotenv.directory=%PROJECT_ROOT%" -Dmaven.repo.local="%PROJECT_ROOT%\.m2" -f "%PROJECT_ROOT%\backend\pom.xml" spring-boot:run >> "%PROJECT_ROOT%\logs\backend-dev.log" 2>> "%PROJECT_ROOT%\logs\backend-dev.err.log"
