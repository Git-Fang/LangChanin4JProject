@echo off
chcp 65001 >nul

echo ============================================
echo   启动 Kafka 服务
echo ============================================
echo.

:: 检查Docker Desktop是否运行
docker version >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker Desktop未运行！
    pause
    exit /b 1
)

echo [1/3] 检查现有容器...
docker ps --format "{{.Names}}" | findstr /i "zookeeper" >nul 2>&1
if errorlevel 1 (
    echo       Zookeeper 未运行，准备启动...
    docker run -d --name zookeeper -p 2181:2181 confluentinc/cp-zookeeper:7.5.0
    if errorlevel 1 (
        echo [错误] Zookeeper 启动失败
        pause
        exit /b 1
    )
    echo       Zookeeper 启动成功
) else (
    echo       Zookeeper 已在运行
)

docker ps --format "{{.Names}}" | findstr /i "kafka" >nul 2>&1
if errorlevel 1 (
    echo       Kafka 未运行，准备启动...
    docker run -d --name kafka -p 9092:9092 ^
        -e KAFKA_BROKER_ID=1 ^
        -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 ^
        -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 ^
        -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 ^
        -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="true" ^
        confluentinc/cp-kafka:7.5.0
    if errorlevel 1 (
        echo [错误] Kafka 启动失败
        pause
        exit /b 1
    )
    echo       Kafka 启动成功，等待初始化...
    timeout /t 10 /nobreak >nul
) else (
    echo       Kafka 已在运行
)

echo.
echo [2/3] 验证Kafka状态...
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list >nul 2>&1
if errorlevel 1 (
    echo [警告] Kafka可能还未就绪，请稍后重试
) else (
    echo       Kafka 已就绪
)

echo.
echo [3/3] 创建测试Topic...
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic ai-chat-request --partitions 3 --replication-factor 1 >nul 2>&1
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic ai-chat-result --partitions 3 --replication-factor 1 >nul 2>&1
echo       Topic 创建完成

echo.
echo ============================================
echo   Kafka 服务启动完成！
echo ============================================
echo.
echo   服务状态:
echo   - Zookeeper: localhost:2181
echo   - Kafka:     localhost:9092
echo   - UI:        http://localhost:8081 (可选)
echo.
echo   使用说明:
echo   1. 确保Zookeeper和Kafka正常运行
echo   2. 运行 deploy-desktop.bat 部署应用
echo   3. 访问 http://localhost:8000/chat-sse.html 测试SSE聊天
echo.
echo ============================================
pause
