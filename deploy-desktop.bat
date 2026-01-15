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
echo       Cleanup old container...
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm -f %CONTAINER_NAME% >nul 2>&1

echo.
echo [6/7] Start application container...
echo       Reading environment variables...

for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    if not "%%a"=="" if not "%%a:~0,1%"=="#" (
        set "%%a=%%b"
    )
)

echo       Starting Docker container...
docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=root -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e AI_EMBEDDINGSTORE_QDRANT_HOST=host.docker.internal -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 -e DeepSeek_API_KEY=%DeepSeek_API_KEY% -e KIMI_API_KEY=%KIMI_API_KEY% -e DASHSCOPE_API_KEY=%DASHSCOPE_API_KEY% -e BAIDU_MAP_API_KEY=%BAIDU_MAP_API_KEY% -e TZ=Asia/Shanghai %IMAGE_NAME%:latest

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
