@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
set "MVN_BIN=%LOCALAPPDATA%\Programs\Apache\apache-maven-3.9.9\bin\mvn.cmd"
call "%MVN_BIN%" %*
exit /b %ERRORLEVEL%
