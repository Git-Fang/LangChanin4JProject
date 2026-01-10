@echo off
title RAG Translation - Safe Mode
color 0A

echo.
echo =========================================
echo   RAG Translation - Stable Mode
echo =========================================
echo.
echo 此版本运行没有外部服务依赖的应用

echo.

:: Check Java
echo [+] Checking Java...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found.
    pause
    exit /b 1
)

:: Set JVM options
set JVM_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC
set APP_OPTS=-Dspring.profiles.active=local -Dserver.port=8000

:: Create directories
mkdir logs 2>nul
mkdir uploads 2>nul

:: Run with local mode enabled
echo.
echo =========================================
echo Starting with Local Configuration...
echo =========================================
echo.
echo Service will be available at:
echo   Main UI   :http://localhost:8000/unified.html
echo   Test Page :http://localhost:8000/test
echo   Status    :http://localhost:8000/local/status
echo.
echo Press Ctrl+C to stop
echo =========================================
echo.

cd /d "%~dp0"
java %JVM_OPTS% %APP_OPTS% -jar "target\RAGTranslation4-1.0-SNAPSHOT.jar" || (
    echo.
    echo [ERROR] Failed to start.
    echo [HINT] Make sure the JAR file exists.
    pause
)
exit /b 0