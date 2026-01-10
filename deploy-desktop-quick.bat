@echo off
echo 正在快速部署 RAGTranslation Docker 环境（使用简化配置）...
echo.

:: 检查Docker Desktop是否运行
docker version >nul 2>nul
if errorlevel 1 (
    echo 错误: Docker Desktop未运行或未安装！
    pause
    exit /b 1
)

:: 设置项目名称
set PROJECT_NAME=ragtranslation

echo.
echo 第一步：检查本地镜像...
docker images mysql | findstr mysql >nul
if errorlevel 1 (
    echo 检测到需要MySQL镜像，正在拉取...
    docker pull mysql || echo "使用已有的MySQL镜像"
)

docker images redis | findstr redis >nul
if errorlevel 1 (
    echo 检测到需要Redis镜像，正在拉取...
    docker pull redis:alpine || echo "使用已有的Redis镜像"
)

docker images rabbitmq:3.12-management | findstr rabbitmq >nul
if errorlevel 1 (
    echo 检测到需要RabbitMQ镜像，正在拉取...
    docker pull rabbitmq:3.12-management || echo "使用已有的RabbitMQ镜像"
)

docker images mongo:6 | findstr mongo >nul
if errorlevel 1 (
    echo 检测到需要MongoDB镜像，正在拉取...
    docker pull mongo:6 || echo "使用已有的MongoDB镜像"
)

docker images qdrant/qdrant:v1.8 | findstr qdrant >nul
if errorlevel 1 (
    echo 检测到需要Qdrant镜像，正在拉取...
    docker pull qdrant/qdrant:v1.8 || echo "使用已有的Qdrant镜像"
)

echo.
echo 第二步：启动基础服务（数据库等）...
docker-compose -f docker-compose-local.yml up -d mysql redis mongo rabbitmq qdrant
if errorlevel 1 (
    echo 警告: 部分镜像可能无法拉取，使用备用方案启动大部分服务...
    :: 使用简化版容器
    echo 启动MySQL...
    docker run -d --name ragtranslation-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=guiguxiaozhi mysql || echo "MySQL启动失败"

    echo 启动Redis...
    docker run -d --name ragtranslation-redis -p 6379:6379 redis:alpine || echo "Redis启动失败"

    echo 启动MongoDB...
    docker run -d --name ragtranslation-mongo -p 27017:27017 -e MONGO_INITDB_DATABASE=chat_db mongo || echo "MongoDB启动失败"

    echo 启动RabbitMQ...
    docker run -d --name ragtranslation-rabbitmq -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=admin rabbitmq:3-management || echo "RabbitMQ启动失败"

    echo 启动Qdrant...
    docker run -d --name ragtranslation-qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.8 || echo "Qdrant启动失败"
)

echo.
echo 第三步：等待数据库服务启动...
set /a wait=0
:wait_db
if !wait! GTR 60 (
    echo 超时等待数据库服务...
    goto :build_app
)
timeout /t 5 /nobreak >nul
docker exec ragtranslation-mysql mysql -u root -proot -e "SELECT 1" >nul 2>nul
if errorlevel 1 (
    set /a wait+=1
    echo 等待数据库中... !wait!/60
    goto :wait_db
)

echo.
:build_app
echo 第四步：构建应用镜像...
docker build -t ragtranslation-app:latest -f Dockerfile .
if errorlevel 1 (
    echo 构建失败，使用本地JAR文件直接运行...
    goto :run_with_jar
)

echo.
echo 第五步：启动应用服务...
docker run -d --name ragtranslation-app -p 8000:8000 ^
  -e SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/guiguxiaozhi?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true ^
  -e SPRING_DATASOURCE_USERNAME=root ^
  -e SPRING_DATASOURCE_PASSWORD=root ^
  -e SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/chat_db ^
  -e SPRING_REDIS_HOST=localhost ^
  -e SPRING_REDIS_PORT=6379 ^
  -e SPRING_RABBITMQ_HOST=localhost ^
  -e SPRING_RABBITMQ_PORT=5672 ^
  -e SPRING_RABBITMQ_USERNAME=admin ^
  -e SPRING_RABBITMQ_PASSWORD=admin ^
  -e AI_EMBEDDINGSTORE_QDRANT_HOST=localhost ^
  -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 ^
  -v %cd%/application-docker.yml:/app/config/application-docker.yml:ro ^
  ragtranslation-app:latest || goto :error_handler

go :show_result

:run_with_jar
echo.
echo 使用本地JAR文件启动应用（需要先在本地编译mvn clean package）...
if exist target\*.jar (
    java -Dspring.profiles.active=docker -Dserver.port=8000 -cp "target/*;target/lib/*" org.fb.Application $
) else (
    echo 错误: 未找到目标JAR文件，请先运行: mvn clean package
    goto :error_handler
)

:show_result
echo.
echo.=================== ===================
echo 部署完成！访问地址:
echo.=================== ===================
echo 统一访问页面: http://localhost:8000/unified.html
echo 应用主页:     http://localhost:8000/
echo RabbitMQ管理: http://localhost:15672 (admin/admin)
echo.=================== ===================
echo.
echo 端口配置:
echo   MySQL: 3306 (root/root)
echo   Redis: 6379
echo   MongoDB: 27017
echo   RabbitMQ: 5672 (管理端口: 15672)
echo   Qdrant: 6333/6334
echo   应用: 8000
echo.
pause
exit /b 0

:error_handler
echo.
echo 发生错误，可能需要手动启动服务
echo 请检查：
echo 1. Docker Desktop是否运行正常
echo 2. 是否安装了Maven（用于本地运行）
echo 3. 端口是否被占用
echo.
echo 手动命令：
echo docker start ragtranslation-mysql ragtranslation-redis ragtranslation-mongo ragtranslation-rabbitmq ragtranslation-qdrant
echo.
pause
exit /b 1

endlocal

:: 注意：如果需要停止所有使用container-name的容器，请运行：
:: docker stop ragtranslation-mysql ragtranslation-redis ragtranslation-mongo ragtranslation-rabbitmq ragtranslation-qdrant ragtranslation-app**