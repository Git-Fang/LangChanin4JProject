@echo off

echo ============================================
echo   RAGTranslation Docker Desktop Deployment
echo ============================================
echo.

set IMAGE_NAME=ragtranslation-app
set CONTAINER_NAME=ragtranslation-app
set APP_PORT=8000

echo [1/6] Check Docker Desktop status...
docker version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Desktop is not running!
    pause
    exit /b 1
)
echo       Docker Desktop is running

echo.
echo [2/7] Setup Docker network and check middleware...
echo       Create/verify Docker network...
docker network create ai-network >nul 2>&1
echo       Network ready

docker ps --format "{{.Names}}" | findstr /i "mysql" >nul 2>&1
if errorlevel 1 (echo       MySQL: Not running) else (echo       MySQL: Running)

docker ps --format "{{.Names}}" | findstr /i "mongo" >nul 2>&1
if errorlevel 1 (echo       MongoDB: Not running) else (echo       MongoDB: Running)

docker ps --format "{{.Names}}" | findstr /i "qdrant" >nul 2>&1
if errorlevel 1 (echo       Qdrant: Not running) else (echo       Qdrant: Running)

docker ps --format "{{.Names}}" | findstr /i "redis" >nul 2>&1
if errorlevel 1 (
    echo       Redis: Not running, starting...
    docker rm -f redis >nul 2>&1
    docker run -d --name redis --network ai-network -p 6379:6379 redis:alpine
    if errorlevel 1 (
        echo       Redis failed to start
    ) else (
        echo       Redis started successfully
    )
) else (echo       Redis: Running)

echo.
echo [3/6] Check Java base image...
docker images eclipse-temurin:17-jre-alpine --format "{{.ID}}" | findstr /r "." >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Base image not found: eclipse-temurin:17-jre-alpine
    pause
    exit /b 1
)
echo       Base image is ready

echo.
echo [4/7] Build Java application...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Maven build failed!
    pause
    exit /b 1
)
echo       Java application built successfully

echo.
echo [5/7] Build Docker image...
docker build -t %IMAGE_NAME%:latest .
if errorlevel 1 (
    echo [ERROR] Docker image build failed!
    pause
    exit /b 1
)
echo       Image built: %IMAGE_NAME%:latest

echo.
echo [6/7] Check .env file for environment variables...
if not exist ".env" (
    echo [WARNING] .env file not found, using default values
)

echo.
echo [7/7] Start application container...
echo       Stopping and removing old container if exists...
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm -f %CONTAINER_NAME% >nul 2>&1
echo       Old container cleaned up
echo.
echo       Starting Docker container with environment variables from .env...

docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% --env-file .env -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e SPRING_REDIS_HOST=172.20.0.5 -e SPRING_REDIS_PORT=6379 -e AI_EMBEDDINGSTORE_QDRANT_HOST=host.docker.internal -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e spring.kafka.bootstrap-servers=kafka:9092 -e TZ=Asia/Shanghai %IMAGE_NAME%:latest

if errorlevel 1 (
    echo [ERROR] Container failed to start!
    pause
    exit /b 1
)

echo       Container started, waiting for initialization...
timeout /t 20 /nobreak >nul

echo.
echo ============================================
echo   Deployment completed!
echo ============================================
echo.
echo   URLs:
echo   ----------------------------------------
echo   SSE Chat:  http://localhost:8000/chat-sse.html
echo   Unified:   http://localhost:8000/unified.html
echo   Home:      http://localhost:8000/
echo   API Docs:  http://localhost:8000/doc.html
echo   ----------------------------------------
echo.
echo   Commands:
echo   Logs:      docker logs -f %CONTAINER_NAME%
echo   Stop:      docker stop %CONTAINER_NAME%
echo   Restart:   docker restart %CONTAINER_NAME%
echo.
echo ============================================
echo.
pause
exit /b 0
