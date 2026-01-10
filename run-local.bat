@echo off
title RAG Translation Local Mode
color 0A

echo.
echo =========================================
echo  RAG Translation - Local JAR Mode
echo =========================================
echo.
echo 直接运行JAR文件，无需Docker
echo.

:: Check if JAR exists
if not exist "target\*.jar" (
    echo [ERROR] No JAR file found in target/
    echo Please run: mvn clean package -DskipTests
    pause
    exit /b 1
)

:: Set port (default 8000)
set PORT=8000
echo [INFO] Using port %PORT%

:: Create dirs
mkdir logs 2>nul
mkdir uploads 2>nul

:: Run the application
echo.
echo Starting RAG Translation System...
echo.
echo Access URL: http://localhost:%PORT%/unified.html
echo Press Ctrl+C to stop
echo.
echo =========================================
echo.

:: Run JAR with Java 17
cd /d "%~dp0"
java -Xms256m -Xmx512m -XX:+UseG1GC -Dserver.port=%PORT% -Dserver.address=0.0.0.0 -jar target/*.jar

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Failed to start application
    echo Please ensure:
    echo 1. Java 17+ is installed
    echo 2. Port %PORT% is available
    echo 3. Required services are running
    pause
)

exit /b %errorlevel%