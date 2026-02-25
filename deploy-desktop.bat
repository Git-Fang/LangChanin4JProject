@echo off

echo ============================================
echo   RAGTranslation Docker Desktop Deployment
echo ============================================
echo.

set IMAGE_NAME=ragtranslation-app
set CONTAINER_NAME=ragtranslation-app
set APP_PORT=8000
set USE_LOCAL_MYSQL=0

echo [1/8] Check Docker Desktop status...
docker version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Desktop is not running!
    pause
    exit /b 1
)
echo       Docker Desktop is running

echo.
echo [2/8] Setup Docker network and check middleware...

echo       Create/verify Docker network...
docker network create ai-network >nul 2>&1
echo       Network ready

echo.
echo       Checking middleware services...

echo       Checking local MySQL service...
sc query MySQL | findstr /i "RUNNING" >nul 2>&1
if not errorlevel 1 (
    echo       Local MySQL service is running
    set USE_LOCAL_MYSQL=1
) else (
    netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>&1
    if not errorlevel 1 (
        echo       Local MySQL is listening on port 3306
        set USE_LOCAL_MYSQL=1
    ) else (
        echo       No local MySQL found
        set USE_LOCAL_MYSQL=0
    )
)

if "%USE_LOCAL_MYSQL%"=="1" (
    echo       Will use local MySQL (localhost:3306)
) else (
    echo       Starting Docker MySQL...
    docker rm -f mysql >nul 2>&1
    docker run -d --name mysql --network ai-network -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=mydocker -v mysql-data:/var/lib/mysql -v %CD%/sql:/docker-entrypoint-initdb.d mysql:8.0-debian
    if errorlevel 1 (
        echo       MySQL failed to start
    ) else (
        echo       MySQL started successfully
        echo       Waiting for MySQL to be ready...
        timeout /t 30 /nobreak >nul
        echo       MySQL is ready
    )
)

docker ps --format "{{.Names}}" | findstr /i "^mongo$" >nul 2>&1
if errorlevel 1 (
    echo       MongoDB: Not running, starting...
    docker rm -f mongo >nul 2>&1
    docker run -d --name mongo --network ai-network -p 27017:27017 -v mongo-data:/data/db mongo:7.0
    if errorlevel 1 (
        echo       MongoDB failed to start
    ) else (
        echo       MongoDB started successfully
        timeout /t 10 /nobreak >nul
    )
) else (echo       MongoDB: Running)

docker ps --format "{{.Names}}" | findstr /i "nacos" >nul 2>&1
if errorlevel 1 (
    echo       Nacos: Not running
) else (
    echo       Nacos: Running
)

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
echo       Checking Qdrant status...

docker ps --format "{{.Names}}" | findstr /i "qdrant" >nul 2>&1
if errorlevel 1 (
    echo       Qdrant: Not running, starting...
    docker rm -f qdrant >nul 2>&1
    docker run -d --name qdrant --network ai-network -p 6333:6333 -p 6334:6334 -v qdrant-data:/qdrant/storage qdrant/qdrant:v1.12.0
    if errorlevel 1 (
        echo       Qdrant failed to start
    ) else (
        echo       Qdrant started successfully
    )
) else (echo       Qdrant: Running)

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
    docker ps --format "{{.Names}}" | findstr /i "^kafka$" >nul 2>&1
    if not errorlevel 1 (
        echo       Kafka: Container running
        set KAFKA_RUNNING=1
    ) else (
        echo       Kafka: Not running
    )
)

echo.
if "%ZOOKEEPER_RUNNING%"=="0" (
    echo [3/8] Start Zookeeper...
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
    echo [3/8] Zookeeper: Skipped (already running)
)

if "%KAFKA_RUNNING%"=="0" (
    echo.
    echo [4/8] Start Kafka...
    docker rm -f kafka >nul 2>&1
    docker run -d --name kafka --network ai-network -p 9092:9092 -e KAFKA_BROKER_ID=1 -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 -e KAFKA_LISTENERS=PLAINTEXT://:9092 -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="true" confluentinc/cp-kafka:7.5.0
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
    echo [4/8] Kafka: Skipped (already running)
)

echo.
echo [5/8] Check Nacos status and start if needed...

docker ps --format "{{.Names}}" | findstr /i "nacos" >nul 2>&1
if errorlevel 1 (
    echo       Nacos container not found, starting...
    
    docker rm -f nacos >nul 2>&1
    docker run -d --name nacos --network ai-network -p 8848:8848 -e MODE=standalone -e NACOS_AUTH_ENABLE=false -e TZ=Asia/Shanghai nacos/nacos-server:v2.4.2
    
    if errorlevel 1 (
        echo [ERROR] Nacos failed to start
        pause
        exit /b 1
    )
    echo       Nacos started successfully
    timeout /t 10 /nobreak >nul
) else (
    echo [5/8] Nacos: Skipped (already running)
)

echo.
echo [6/8] Check Java base image...
docker images maven:3.9.9-eclipse-temurin-17 --format "{{.ID}}" | findstr /r "." >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Base image not found: maven:3.9.9-eclipse-temurin-17
    echo       Pulling Maven base image...
    docker pull maven:3.9.9-eclipse-temurin-17
    if errorlevel 1 (
        echo [ERROR] Failed to pull Maven base image
        pause
        exit /b 1
    )
)
echo       Base image is ready

echo.
echo [7/8] Build Java application...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Maven build failed!
    pause
    exit /b 1
)
echo       Java application built successfully

echo.
echo [8/9] Build Docker image...
docker build -t %IMAGE_NAME%:latest .
if errorlevel 1 (
    echo [ERROR] Docker image build failed!
    pause
    exit /b 1
)
echo       Image built: %IMAGE_NAME%:latest

echo.
echo       Starting application container...

if "%USE_LOCAL_MYSQL%"=="1" (
    echo       Using local MySQL (localhost:3306)
    docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% --env-file .env --add-host=host.docker.internal:host-gateway -e SPRING_PROFILES_ACTIVE=docker -e NACOS_SERVER_ADDR=nacos:8848 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e SPRING_REDIS_HOST=redis -e SPRING_REDIS_PORT=6379 -e AI_EMBEDDINGSTORE_QDRANT_HOST=qdrant -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e spring.kafka.bootstrap-servers=kafka:9092 -e TZ=Asia/Shanghai --dns=8.8.8.8 --dns=114.114.114.114 %IMAGE_NAME%:latest
) else (
    echo       Using Docker MySQL (mysql:3306)
    docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% --env-file .env --add-host=host.docker.internal:host-gateway -e SPRING_PROFILES_ACTIVE=docker -e NACOS_SERVER_ADDR=nacos:8848 -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e SPRING_REDIS_HOST=redis -e SPRING_REDIS_PORT=6379 -e AI_EMBEDDINGSTORE_QDRANT_HOST=qdrant -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e spring.kafka.bootstrap-servers=kafka:9092 -e TZ=Asia/Shanghai --dns=8.8.8.8 --dns=114.114.114.114 %IMAGE_NAME%:latest
)

echo.
echo       Stopping and removing old container if exists...
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm -f %CONTAINER_NAME% >nul 2>&1
timeout /t 2 /nobreak >nul

echo       Checking for conflicting containers...
for /f "tokens=*" %%i in ('docker ps -a --filter "name=%CONTAINER_NAME%" --format "{{.ID}}"') do (
    if not "%%i"=="" (
        echo       Found conflicting container %%i, removing...
        docker rm -f %%i >nul 2>&1
    )
)
timeout /t 2 /nobreak >nul

echo       Old container cleaned up

echo.
echo       Starting Docker container with environment variables from .env...

echo       Checking knowledge directory...
if not exist "knowledge" (
    echo [WARNING] knowledge directory not found, creating...
    mkdir knowledge
    echo       Please add your knowledge base files to the knowledge/ directory
) else (
    echo       Knowledge directory found, will be mounted to container
)

if "%USE_LOCAL_MYSQL%"=="1" (
    echo       Using local MySQL (localhost:3306)
    docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% --env-file .env --add-host=host.docker.internal:host-gateway -e SPRING_PROFILES_ACTIVE=docker -e NACOS_SERVER_ADDR=nacos:8848 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e SPRING_REDIS_HOST=redis -e SPRING_REDIS_PORT=6379 -e AI_EMBEDDINGSTORE_QDRANT_HOST=qdrant -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e spring.kafka.bootstrap-servers=kafka:9092 -e TZ=Asia/Shanghai --dns=8.8.8.8 --dns=114.114.114.114 -v %CD%/knowledge:/app/knowledge %IMAGE_NAME%:latest
) else (
    echo       Using Docker MySQL (mysql:3306)
    docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% --env-file .env --add-host=host.docker.internal:host-gateway -e SPRING_PROFILES_ACTIVE=docker -e NACOS_SERVER_ADDR=nacos:8848 -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e SPRING_REDIS_HOST=redis -e SPRING_REDIS_PORT=6379 -e AI_EMBEDDINGSTORE_QDRANT_HOST=qdrant -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e spring.kafka.bootstrap-servers=kafka:9092 -e TZ=Asia/Shanghai --dns=8.8.8.8 --dns=114.114.114.114 %IMAGE_NAME%:latest
)

if errorlevel 1 (
    echo [WARNING] Container failed to start, attempting to clean up and retry...
    docker stop %CONTAINER_NAME% >nul 2>&1
    docker rm -f %CONTAINER_NAME% >nul 2>&1
    timeout /t 3 /nobreak >nul
    
    echo       Retrying container start...
    if "%USE_LOCAL_MYSQL%"=="1" (
        docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% --env-file .env --add-host=host.docker.internal:host-gateway -e SPRING_PROFILES_ACTIVE=docker -e NACOS_SERVER_ADDR=nacos:8848 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e SPRING_REDIS_HOST=redis -e SPRING_REDIS_PORT=6379 -e AI_EMBEDDINGSTORE_QDRANT_HOST=qdrant -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e spring.kafka.bootstrap-servers=kafka:9092 -e TZ=Asia/Shanghai --dns=8.8.8.8 --dns=114.114.114.114 %IMAGE_NAME%:latest
    ) else (
        docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% --env-file .env --add-host=host.docker.internal:host-gateway -e SPRING_PROFILES_ACTIVE=docker -e NACOS_SERVER_ADDR=nacos:8848 -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db -e SPRING_REDIS_HOST=redis -e SPRING_REDIS_PORT=6379 -e AI_EMBEDDINGSTORE_QDRANT_HOST=qdrant -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 -e spring.kafka.bootstrap-servers=kafka:9092 -e TZ=Asia/Shanghai --dns=8.8.8.8 --dns=114.114.114.114 %IMAGE_NAME%:latest
    )
    
    if errorlevel 1 (
        echo [ERROR] Container failed to start after retry!
        pause
        exit /b 1
    ) else (
        echo       Container started successfully on retry!
    )
)

echo       Container started, waiting for initialization...
timeout /t 20 /nobreak >nul

echo.
echo [10/10] Start monitoring services (Prometheus, Grafana, Exporters)...

echo       Copy Docker Prometheus config...
copy /Y prometheus-docker.yml prometheus-temp.yml >nul 2>&1
if exist "prometheus-docker.yml" (
    echo       Using Docker Prometheus config
)

echo.
echo       Starting Prometheus...
docker rm -f prometheus >nul 2>&1
docker run -d --name prometheus --network ai-network -p 9090:9090 ^
    -v %CD%/prometheus-docker.yml:/etc/prometheus/prometheus.yml:ro ^
    prom/prometheus:v2.47.0
if errorlevel 1 (
    echo [WARNING] Prometheus failed to start
) else (
    echo       Prometheus started on port 9090
)

echo.
echo       Starting Grafana...
docker rm -f grafana >nul 2>&1
docker run -d --name grafana --network ai-network -p 3000:3000 ^
    -e GF_SECURITY_ADMIN_USER=admin ^
    -e GF_SECURITY_ADMIN_PASSWORD=admin123 ^
    -e TZ=Asia/Shanghai ^
    -v grafana-data:/var/lib/grafana ^
    -v %CD%/monitoring/grafana-provisioning/dashboards:/etc/grafana/provisioning/dashboards:ro ^
    -v %CD%/monitoring/grafana-provisioning/datasources:/etc/grafana/provisioning/datasources:ro ^
    grafana/grafana:10.1.10
if errorlevel 1 (
    echo [WARNING] Grafana failed to start
) else (
    echo       Grafana started on port 3000
)

echo.
echo       Starting MongoDB Exporter...
docker rm -f mongodb-exporter >nul 2>&1
docker run -d --name mongodb-exporter --network ai-network -p 9216:9216 ^
    -e MONGODB_URI=mongodb://mongo:27017 ^
    percona/mongodb_exporter:0.39.0 ^
    --mongodb.uri=mongodb://mongo:27017 ^
    --collector.collstats ^
    --collector.dbstats ^
    --collector.indexstats
if errorlevel 1 (
    echo [WARNING] MongoDB Exporter failed to start
) else (
    echo       MongoDB Exporter started on port 9216
)

echo.
echo       Starting Redis Exporter...
docker rm -f redis-exporter >nul 2>&1
docker run -d --name redis-exporter --network ai-network -p 9121:9121 ^
    -e REDIS_ADDR=redis://redis:6379 ^
    oliver006/redis_exporter:latest
if errorlevel 1 (
    echo [WARNING] Redis Exporter failed to start
) else (
    echo       Redis Exporter started on port 9121
)

echo.
echo       Starting Kafka UI...
docker rm -f kafka-ui >nul 2>&1
docker run -d --name kafka-ui --network ai-network -p 8081:8080 ^
    -e KAFKA_CLUSTERS_0_NAME=local ^
    -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:9092 ^
    -e KAFKA_CLUSTERS_0_ZOOKEEPER=zookeeper:2181 ^
    provectuslabs/kafka-ui:latest
if errorlevel 1 (
    echo [WARNING] Kafka UI failed to start
) else (
    echo       Kafka UI started on port 8081
)

timeout /t 5 /nobreak >nul

echo.
echo       Starting Kafka UI...
rem Check if kafka-ui is already running
docker ps --format "{{.Names}}" | findstr /i "kafka-ui" >nul 2>&1
if not errorlevel 1 (
    echo       Kafka UI: Already running
) else (
    rem Start Kafka UI with direct connection to existing Kafka/Zookeeper services
    docker run -d --name kafka-ui --network ai-network -p 8081:8080 ^
        -e KAFKA_CLUSTERS_0_NAME=local ^
        -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:9092 ^
        -e KAFKA_CLUSTERS_0_ZOOKEEPER=zookeeper:2181 ^
        provectuslabs/kafka-ui:latest
    if errorlevel 1 (
        echo [WARNING] Failed to start Kafka UI
    ) else (
        echo       Kafka UI started successfully
    )
)

echo.
echo ============================================
echo   Deployment completed!
echo ============================================
echo.
echo   Application URLs:
echo   ----------------------------------------
echo   Nacos:    http://localhost:8848/nacos
echo   SSE Chat: http://localhost:8000/chat-sse.html
echo   Unified:  http://localhost:8000/unified.html
echo   Home:     http://localhost:8000/
echo   API Docs: http://localhost:8000/doc.html
echo   ----------------------------------------
echo.
echo   Monitoring URLs:
echo   ----------------------------------------
echo   Grafana:  http://localhost:3000 (admin/admin123)
echo   Dashboards: http://localhost:3000/dashboards
echo   Prometheus: http://localhost:9090
echo   Kafka UI: http://localhost:8081
echo   ----------------------------------------
echo.
echo   Commands:
echo   ----------------------------------------
echo   Nacos:    http://localhost:8848/nacos (default login: nacos/nacos)
echo   App:      docker logs -f %CONTAINER_NAME%
echo   Stop App: docker stop %CONTAINER_NAME%
echo   Restart:  docker restart %CONTAINER_NAME%
echo   ----------------------------------------
echo.
echo ============================================
echo.
pause
exit /b 0
