@echo off
setlocal
cd /d "%~dp0\.."
call gradlew.bat -I gradle\run-folia.init.gradle runFolia %*
exit /b %ERRORLEVEL%
