@echo off
title RAG Translation Local Server
color 0A

echo.
echo =========================================
echo   RAG Translation System - Local Server
echo =========================================
echo.

:: Check Java version and JAR file
echo [+] Checking Java environment...
java -version 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found. Please install Java 17 or higher.
    pause
    exit /b 1
)

:: Check for specific JAR file
if not exist "target\RAGTranslation4-1.0-SNAPSHOT.jar" (
    echo [ERROR] JAR file not found at target\RAGTranslation4-1.0-SNAPSHOT.jar
    echo [HINT] Please build with: mvn clean package -DskipTests
    pause
    exit /b 1
)

:: Set port
echo [+] Starting RAG Translation System...
echo.
echo INFO: Using local databases (MongoDB, MySQL, etc.)
echo INFO: Make sure these services are running locally
set PORT=8000

:: Create directories
mkdir logs 2>nul
mkdir uploads 2>nul

:: Start the application
echo [+] Launching application on port %PORT%
echo [+] Access URLs:
echo   - Main UI: http://localhost:%PORT%/unified.html
echo   - API Docs: http://localhost:%PORT%/doc.html
echo.
echo [+] Server logs will appear below:
echo ==========================================
echo.

:: Run with optimizations
cd /d "%~dp0"
java -Xms256m -Xmx512m -XX:+UseG1GC -Dserver.port=%PORT% -Dserver.address=0.0.0.0 -jar "target\RAGTranslation4-1.0-SNAPSHOT.jar"

:: If error occurred
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Application failed to start.
    echo.
    echo Possible issues:
    echo 1. Port %PORT% is already in use
    echo 2. Missing required configuration files
    echo 3. Database connection issues
    echo.
    echo Try checking the logs in the application output above.
    pause
)

exit /b %errorlevel%