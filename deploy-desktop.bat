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
echo       Docker Desktop is running

echo.
echo [2/7] Setup Docker network and check middleware...

echo       Create/verify Docker network...
docker network create ai-network >nul 2>&1
echo       Network ready

echo.
echo       Checking middleware services...

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
echo       Checking Zookeeper and Kafka status...

set ZOOKEEPER_RUNNING=0
set KAFKA_RUNNING=0

netstat -ano | findstr ":2181" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo       Zookeeper: Already running on port 2181
    set ZOOKEEPER_RUNNING=1
) else (
    docker ps --format "{{.Names}}" | findstr /i "zookeeper" >nul 2>&1
    if not errorlevel 1 (
        echo       Zookeeper: Container running
        set ZOOKEEPER_RUNNING=1
    ) else (
        echo       Zookeeper: Not running
    )
)

netstat -ano | findstr ":9092" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo       Kafka: Already running on port 9092
    set KAFKA_RUNNING=1
) else (
    docker ps --format "{{.Names}}" | findstr /i "kafka" >nul 2>&1
    if not errorlevel 1 (
        echo       Kafka: Container running
        set KAFKA_RUNNING=1
    ) else (
        echo       Kafka: Not running
    )
)

echo.
if "%ZOOKEEPER_RUNNING%"=="0" (
    echo [3/7] Start Zookeeper...
    docker rm -f zookeeper >nul 2>&1
    docker run -d --name zookeeper --network ai-network -p 2181:2181 -e ZOOKEEPER_CLIENT_PORT=2181 -e ZOOKEEPER_TICK_TIME=2000 confluentinc/cp-zookeeper:7.5.0
    if errorlevel 1 (
        echo [ERROR] Zookeeper failed to start
        pause
        exit /b 1
    )
    echo       Zookeeper started successfully
    timeout /t 5 /nobreak >nul
) else (
    echo [3/7] Zookeeper: Skipped (already running)
)

if "%KAFKA_RUNNING%"=="0" (
    echo.
    echo [4/7] Start Kafka...
    docker rm -f kafka >nul 2>&1
    docker run -d --name kafka --network ai-network -p 9092:9092 -e KAFKA_BROKER_ID=1 -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 -e KAFKA_LISTENERS=PLAINTEXT://:9092 -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="true" confluentinc/cp-kafka:7.5.0
    if errorlevel 1 (
        echo [ERROR] Kafka failed to start
        pause
        exit /b 1
    )
    echo       Kafka started successfully
    timeout /t 10 /nobreak >nul
    
    echo.
    echo       Verifying Kafka status...
    docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list >nul 2>&1
    if errorlevel 1 (
        echo [WARNING] Kafka may not be ready yet, waiting additional time...
        timeout /t 20 /nobreak >nul
    ) else (
        echo       Kafka is ready
    )
) else (
    echo [4/7] Kafka: Skipped (already running)
)

echo.
echo [5/7] Check Java base image...
docker images eclipse-temurin:17-jre-alpine --format "{{.ID}}" | findstr /r "." >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Base image not found: eclipse-temurin:17-jre-alpine
    pause
    exit /b 1
)
echo       Base image is ready

echo.
echo [6/7] Build Java application...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Maven build failed!
    pause
    exit /b 1
)
echo       Java application built successfully

echo.
echo [7/7] Build Docker image...
docker build -t %IMAGE_NAME%:latest .
if errorlevel 1 (
    echo [ERROR] Docker image build failed!
    pause
    exit /b 1
)
echo       Image built: %IMAGE_NAME%:latest

echo.
echo       Check .env file for environment variables...
if not exist ".env" (
    echo [WARNING] .env file not found, using default values
)

echo.
echo       Stopping and removing old container if exists...
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm -f %CONTAINER_NAME% >nul 2>&1
echo       Old container cleaned up

echo.
echo       Starting Docker container with environment variables from .env...

docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% --env-file .env -e SPRING_PROFILES_ACTIVE=docker -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e SPRING_REDIS_HOST=redis -e SPRING_REDIS_PORT=6379 -e AI_EMBEDDINGSTORE_QDRANT_HOST=host.docker.internal -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e spring.kafka.bootstrap-servers=kafka:9092 -e TZ=Asia/Shanghai %IMAGE_NAME%:latest

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
