@echo off
title RAG Translation - Final Safe Mode
color 0A

echo.
echo =========================================
echo   RAG Translation - Standalone Mode
echo =========================================
echo.
echo 临时解决方案：禁用所有外部服务依赖

echo.

:: Set safe configuration
set JVM_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC
set APP_OPTS=-Dserver.port=8000
set APP_OPTS=%APP_OPTS% -Dai.deepSeek.enabled=false
set APP_OPTS=%APP_OPTS% -Dai.kimi.enabled=false
set APP_OPTS=%APP_OPTS% -Dai.dashscope.enabled=false
set APP_OPTS=%APP_OPTS% -Dai.ollama.enabled=false
set APP_OPTS=%APP_OPTS% -Dai.mcp.enabled=false
set APP_OPTS=%APP_OPTS% -Dspring.profiles.active=local

:: Create directories
mkdir logs 2>nul
mkdir uploads 2>nul

echo [+] Starting with safe configuration...
echo [+] External services disabled for local testing.
echo.
echo =========================================
echo Server will be available at:
echo   Test Page   : http://localhost:8000/test-local.html
echo   Main UI     : http://localhost:8000/unified.html
echo   Status      : http://localhost:8000/test
echo   Local Status: http://localhost:8000/local/status
echo.
echo Press Ctrl+C to stop
@echo =========================================
echo.

cd /d "%~dp0"
"D:/Program Files/Java/jdk-17.0.2/bin/java" %JVM_OPTS% %APP_OPTS% -jar "target\RAGTranslation4-1.0-SNAPSHOT.jar" || (
    echo.
    echo [ERROR] Failed to start application.
    echo [HINT] Check if Java 17+ is installed and JAR file exists.
    pause
)
exit /b 0