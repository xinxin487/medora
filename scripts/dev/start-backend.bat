@echo off
setlocal

set "PROJECT_ROOT=%~dp0..\.."
cd /d "%PROJECT_ROOT%"

if not exist "logs" mkdir "logs"

set "JAVA_HOME=D:\BOS\medoraresourse\jdk-21.0.10"
set "MAVEN_HOME=D:\BOS\medoraresourse\apache-maven-3.9.15"
set "PATH=%MAVEN_HOME%\bin;%JAVA_HOME%\bin;%PATH%"

cd /d "%PROJECT_ROOT%\backend"

echo [%date% %time%] Starting Medora backend...
echo [%date% %time%] Starting Medora backend...>> "%PROJECT_ROOT%\logs\backend-dev.log"
call "%MAVEN_HOME%\bin\mvn.cmd" -Dmaven.repo.local="%PROJECT_ROOT%\.m2" spring-boot:run >> "%PROJECT_ROOT%\logs\backend-dev.log" 2>> "%PROJECT_ROOT%\logs\backend-dev.err.log"
