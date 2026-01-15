@echo off
chcp 936 >nul
setlocal enabledelayedexpansion

echo ============================================
echo   RAGTranslation Docker Desktop 部署工具
echo ============================================
echo.

set IMAGE_NAME=ragtranslation-app
set CONTAINER_NAME=ragtranslation-app
set APP_PORT=8000

echo [1/6] 检查Docker Desktop状态...
docker version >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker Desktop未运行或未安装！
    echo        请先启动Docker Desktop后重试。
    pause
    exit /b 1
)
echo       Docker Desktop运行正常

echo.
echo [2/7] 检查依赖的中间件容器...
set MISSING_SERVICES=

docker ps --format "{{.Names}}" | findstr /i "mysql" >nul 2>&1
if errorlevel 1 (set MISSING_SERVICES=!MISSING_SERVICES! MySQL) else (echo       MySQL: 运行中)

docker ps --format "{{.Names}}" | findstr /i "mongo" >nul 2>&1
if errorlevel 1 (set MISSING_SERVICES=!MISSING_SERVICES! MongoDB) else (echo       MongoDB: 运行中)

docker ps --format "{{.Names}}" | findstr /i "qdrant" >nul 2>&1
if errorlevel 1 (set MISSING_SERVICES=!MISSING_SERVICES! Qdrant) else (echo       Qdrant: 运行中)

docker ps --format "{{.Names}}" | findstr /i "kafka" >nul 2>&1
if errorlevel 1 (
    echo       Kafka: 未运行，准备启动...
    set MISSING_SERVICES=!MISSING_SERVICES! Kafka
) else (echo       Kafka: 运行中)

docker ps --format "{{.Names}}" | findstr /i "zookeeper" >nul 2>&1
if errorlevel 1 (
    echo       Zookeeper: 未运行，准备启动...
    set MISSING_SERVICES=!MISSING_SERVICES! Zookeeper
) else (echo       Zookeeper: 运行中)

if not "!MISSING_SERVICES!"=="" (
    echo.
    echo [警告] 以下中间件未运行:!MISSING_SERVICES!
    echo.
    echo [信息] 正在尝试启动缺失的中间件...

    docker ps --format "{{.Names}}" | findstr /i "zookeeper" >nul 2>&1
    if errorlevel 1 (
        echo       正在启动 Zookeeper...
        docker rm -f zookeeper >nul 2>&1
        docker run -d --name zookeeper -p 2181:2181 -e ZOOKEEPER_CLIENT_PORT=2181 -e ZOOKEEPER_TICK_TIME=2000 confluentinc/cp-zookeeper:7.5.0
        if errorlevel 1 (
            echo [警告] Zookeeper 启动失败
        ) else (
            echo       Zookeeper 启动成功
            timeout /t 5 /nobreak >nul
        )
    )

    docker ps --format "{{.Names}}" | findstr /i "kafka" >nul 2>&1
    if errorlevel 1 (
        echo       正在启动 Kafka...
        docker rm -f kafka >nul 2>&1
        docker run -d --name kafka -p 9092:9092 -e KAFKA_BROKER_ID=1 -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="true" -e KAFKA_LOG_DIRS=/var/lib/kafka/data bitnami/kafka:3.8
        if errorlevel 1 (
            echo [警告] Kafka 启动失败，将继续部署
        ) else (
            echo       Kafka 启动成功
            timeout /t 10 /nobreak >nul
        )
    )
)

echo.
echo [3/6] 检查Java基础镜像...
docker images eclipse-temurin:17-jre-alpine --format "{{.ID}}" | findstr /r "." >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到基础镜像 eclipse-temurin:17-jre-alpine
    echo        请先手动拉取: docker pull eclipse-temurin:17-jre-alpine
    pause
    exit /b 1
)
echo       基础镜像已就绪

echo.
echo [4/7] 构建Java应用...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [错误] Maven构建失败！
    pause
    exit /b 1
)
echo       Java应用构建成功

echo.
echo [5/7] 构建Docker镜像...
docker build -t %IMAGE_NAME%:latest .
if errorlevel 1 (
    echo [错误] Docker镜像构建失败！
    pause
    exit /b 1
)
echo       镜像构建成功: %IMAGE_NAME%:latest

echo.
echo       正在清理旧容器...
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm -f %CONTAINER_NAME% >nul 2>&1
timeout /t 3 /nobreak >nul

echo.
echo [6/7] 启动应用容器...

for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    if not "%%a"=="" if not "%%a:~0,1%"=="#" (
        set "%%a=%%b"
    )
)

docker run -d --name %CONTAINER_NAME% -p %APP_PORT%:%APP_PORT% -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=root -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e AI_EMBEDDINGSTORE_QDRANT_HOST=host.docker.internal -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 -e DeepSeek_API_KEY=%DeepSeek_API_KEY% -e KIMI_API_KEY=%KIMI_API_KEY% -e DASHSCOPE_API_KEY=%DASHSCOPE_API_KEY% -e BAIDU_MAP_API_KEY=%BAIDU_MAP_API_KEY% -e TZ=Asia/Shanghai %IMAGE_NAME%:latest

if errorlevel 1 (
    echo [错误] 容器启动失败！
    pause
    exit /b 1
)

echo       容器已启动，等待应用初始化...
timeout /t 20 /nobreak >nul

echo.
echo ============================================
echo   部署完成！
echo ============================================
echo.
echo   访问地址:
echo   ----------------------------------------
echo   SSE实时聊天:  http://localhost:%APP_PORT%/chat-sse.html
echo   统一客户端:   http://localhost:%APP_PORT%/unified.html
echo   应用主页:     http://localhost:%APP_PORT%/
echo   API文档:      http://localhost:%APP_PORT%/doc.html
echo   ----------------------------------------
echo.
echo   常用命令:
echo   查看日志:  docker logs -f %CONTAINER_NAME%
echo   停止应用:  docker stop %CONTAINER_NAME%
echo   重启应用:  docker restart %CONTAINER_NAME%
echo.
echo ============================================
echo.
pause
endlocal
exit /b 0
