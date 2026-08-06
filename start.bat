@echo off
set "JAVA_HOME=D:\BOS\medoraresourse\jdk-21.0.10"
set "MAVEN_HOME=D:\BOS\medoraresourse\apache-maven-3.9.15"
set "PATH=%MAVEN_HOME%\bin;%JAVA_HOME%\bin;%PATH%"
cd /d D:\BOS\Medora

echo JAVA_HOME=%JAVA_HOME%
echo MAVEN_HOME=%MAVEN_HOME%
where java
where mvn
java -version
mvn -version

cmd /k