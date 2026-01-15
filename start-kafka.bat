@echo off

echo ============================================
echo   Kafka Startup Script
echo ============================================
echo.

echo [1/4] Cleanup old containers...
docker rm -f zookeeper kafka 2>nul
echo       Cleanup completed

echo.
echo [2/4] Create Docker network...
docker network rm ai-network 2>nul
docker network create ai-network >nul 2>&1
echo       Network created

echo.
echo [3/4] Start Zookeeper...
docker run -d --name zookeeper --network ai-network -p 2181:2181 -e ZOOKEEPER_CLIENT_PORT=2181 -e ZOOKEEPER_TICK_TIME=2000 confluentinc/cp-zookeeper:7.5.0
if errorlevel 1 (
    echo       Zookeeper failed to start
    pause
    exit /b 1
)
echo       Zookeeper started successfully
timeout /t 5 /nobreak >nul

echo.
echo [4/4] Start Kafka...
docker run -d --name kafka --network ai-network -p 9092:9092 -e KAFKA_BROKER_ID=1 -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 -e KAFKA_LISTENERS=PLAINTEXT://:9092 -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="true" bitnami/kafka:3.8
if errorlevel 1 (
    echo       Kafka failed to start
    pause
    exit /b 1
)
echo       Kafka started successfully

echo.
echo ============================================
echo   Kafka Services Started!
echo ============================================
echo.
echo   Services:
echo   - Zookeeper: localhost:2181
echo   - Kafka:     localhost:9092
echo.
echo   Please wait 30 seconds for Kafka to initialize...
echo ============================================
echo.
timeout /t 30 /nobreak >nul

echo Verify Kafka status...
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list >nul 2>&1
if errorlevel 1 (
    echo [WARNING] Kafka may not be ready yet
) else (
    echo       Kafka is ready
)
echo.
pause
