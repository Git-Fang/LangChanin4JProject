@echo off

echo ============================================
echo   RAGTranslation Docker Desktop Deployment
echo ============================================
echo.

set IMAGE_NAME=ragtranslation-app
set CONTAINER_NAME=ragtranslation-app
set APP_PORT=8000

echo [1/7] Check Docker Desktop status...
docker version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Desktop is not running!
    pause
    exit /b 1
)
echo Docker Desktop is running

echo.
echo [2/7] Setup Docker network...
docker network create ai-network >nul 2>&1
echo Network ready

echo.
echo [3/7] Check middleware services...
docker ps --format "{{.Names}}" | findstr /i "redis" >nul 2>&1
if errorlevel 1 (
    echo Starting Redis...
    docker rm -f redis >nul 2>&1
    docker run -d --name redis --network ai-network -p 6379:6379 redis:alpine >nul 2>&1
    echo Redis started
)

docker ps --format "{{.Names}}" | findstr /i "zookeeper" >nul 2>&1
if errorlevel 1 (
    echo Starting Zookeeper...
    docker rm -f zookeeper >nul 2>&1
    docker run -d --name zookeeper --network ai-network -p 2181:2181 -e ZOOKEEPER_CLIENT_PORT=2181 confluentinc/cp-zookeeper:7.5.0 >nul 2>&1
    echo Zookeeper started
) else (
    echo Zookeeper: already running
)

docker ps --format "{{.Names}}" | findstr /i "kafka" >nul 2>&1
if errorlevel 1 (
    echo Starting Kafka...
    docker rm -f kafka >nul 2>&1
    docker run -d --name kafka --network ai-network -p 9092:9092 -e KAFKA_BROKER_ID=1 -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 -e KAFKA_LISTENERS=PLAINTEXT://:9092 confluentinc/cp-kafka:7.5.0 >nul 2>&1
    echo Kafka started
    timeout /t 10 /nobreak >nul
) else (
    echo Kafka: already running
)

docker ps --format "{{.Names}}" | findstr /i "nacos" >nul 2>&1
if errorlevel 1 (
    echo.
    echo [4/7] Start Nacos v3.1.1...
    docker rm -f nacos >nul 2>&1
    echo       Starting Nacos with auth enabled...
    docker run -d --name nacos --network ai-network -p 8848:8848 -p 9848:9848 -p 9849:9849 ^
        -e MODE=standalone ^
        -e NACOS_AUTH_ENABLE=true ^
        -e NACOS_AUTH_TOKEN=Ymx1ZWJsdWU= ^
        -e NACOS_AUTH_IDENTITY_KEY=nacos ^
        -e NACOS_AUTH_IDENTITY_VALUE=nacos ^
        nacos/nacos-server:v3.1.1 >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] Nacos failed to start
        pause
        exit /b 1
    )
    echo Nacos started successfully
    timeout /t 15 /nobreak >nul
    echo Nacos is ready
) else (
    echo.
    echo [4/7] Nacos: already running
)

echo.
echo [5/7] Build Java application...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Maven build failed!
    pause
    exit /b 1
)
echo Build successful

echo.
echo [6/7] Build Docker image...
docker build -t %IMAGE_NAME%:latest . >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker build failed!
    pause
    exit /b 1
)
echo Image built

echo.
echo [7/7] Start application container...
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm %CONTAINER_NAME% >nul 2>&1

docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% --env-file .env -e SPRING_PROFILES_ACTIVE=docker -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e SPRING_REDIS_HOST=redis -e AI_EMBEDDINGSTORE_QDRANT_HOST=host.docker.internal -e spring.kafka.bootstrap-servers=kafka:9092 -e NACOS_ENABLED=false %IMAGE_NAME%:latest >nul 2>&1

if errorlevel 1 (
    echo [ERROR] Container failed to start!
    pause
    exit /b 1
)

echo Container started
timeout /t 20 /nobreak >nul

echo.
echo ============================================
echo Deployment completed!
echo ============================================
echo.
echo URLs:
echo   SSE Chat: http://localhost:8000/chat-sse.html
echo   Unified: http://localhost:8000/unified.html
echo   Nacos: http://localhost:8848/nacos
echo.
echo Nacos Login: nacos / nacos
echo.
echo Commands:
echo   App Logs: docker logs -f %CONTAINER_NAME%
echo   Stop App: docker stop %CONTAINER_NAME%
echo   Nacos Logs: docker logs -f nacos
echo.
pause
