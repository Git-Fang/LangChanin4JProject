@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
echo 正在启动 RAGTranslation Docker 部署...
echo.

:: 检查Docker Desktop是否运行
docker version >nul 2>nul
if errorlevel 1 (
    echo 错误: Docker Desktop未运行或未安装！
    echo 请先启动Docker Desktop然后重试。
    pause
    exit /b 1
)

:: 设置项目名称
set PROJECT_NAME=ragtranslation

echo.
echo 检查本地镜像...
docker images mysql >nul 2>nul
if errorlevel 1 (
    echo 未找到MySQL镜像，将跳过docker-compose的build避免网络问题...
    goto :fallback_mode
)

echo.
echo 尝试使用docker-compose方式启动...
echo.
echo 第一步：停止已有的相关容器...
docker-compose down 2>nul

:: 等待5秒
ping -n 5 127.0.0.1 >nul

echo.
echo 第二步：启动依赖服务（数据库等）...
docker-compose up -d mysql redis mongo rabbitmq qdrant
if errorlevel 1 (
    echo Docker compose方式失败，进入手动模式...
    goto :manual_mode
)

echo.
echo 等待数据库服务启动...
timeout /t 20 /nobreak >nul

echo.
echo 第三步：构建应用镜像...
docker-compose build app
if errorlevel 1 (
    echo 构建失败，进入手动模式...
    docker-compose down
    goto :manual_mode
)

echo.
echo 第四步：启动应用服务...
docker-compose up -d app
if errorlevel 1 (
    echo 应用启动失败，进入手动模式...
    goto :manual_mode
)

echo 第五步：等待应用就绪...
timeout /t 20 /nobreak >nul

goto :show_status

:manual_mode
echo.
echo ====== 手动模式启动 ======
echo.
echo 手动启动基础服务...
echo 1. 启动MySQL...
docker run -d --name ragtranslation-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=guiguxiaozhi mysql || echo "MySQL可能已经存在，尝试重启..."
docker restart ragtranslation-mysql || echo "MySQL容器不存在，可能需要手动拉取镜像"

ping -n 3 127.0.0.1 >nul

echo 2. 启动Redis...
docker run -d --name ragtranslation-redis -p 6379:6379 redis:alpine || echo "Redis可能已经存在,重启..."
docker restart ragtranslation-redis || echo "Redis容器不存在"

ping -n 3 127.0.0.1 >nul

echo 3. 启动MongoDB...
docker run -d --name ragtranslation-mongo -p 27017:27017 -e MONGO_INITDB_DATABASE=chat_db mongo || echo "Mongo可能已经存在..."
docker restart ragtranslation-mongo || echo "MongoDB容器不存在"

ping -n 3 127.0.0.1 >nul

echo 4. 启动RabbitMQ...
docker run -d --name ragtranslation-rabbitmq -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=admin rabbitmq:3-management || echo "RabbitMQ可能已经存在..."
 docker restart ragtranslation-rabbitmq || echo "RabbitMQ容器不存在"

ping -n 3 127.0.0.1 >nul

echo 5. 启动Qdrant...
docker run -d --name ragtranslation-qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant || echo "Qdrant可能已经存在..."
docker restart ragtranslation-qdrant || echo "Qdrant容器不存在"

:fallback_mode
:show_status
echo.
echo 等待几秒，检查所有服务状态...
ping -n 10 127.0.0.1 >nul

echo.
echo =================== 部署状态检查 ===================
echo.
echo 【访问地址】
echo 统一访问页面: http://localhost:8000/unified.html
echo 应用主页:     http://localhost:8000/
echo RabbitMQ管理: http://localhost:15672 (admin/admin)
echo.
echo 如服务访问异常，请运行 health-check.bat 检查状态
echo.
echo 端口配置:
echo   MySQL: 3306 (root/root)
echo   Redis: 6379
echo   MongoDB: 27017
echo   RabbitMQ: 5672 (管理端口: 15672)
echo   Qdrant: 6333/6334
echo   应用: 8000
echo.
echo 注意：如果部分服务需要拉取镜像，请手动执行：
echo  docker start ragtranslation-mysql
echo  docker start ragtranslation-redis
echo  docker start ragtranslation-mongo
echo  docker start ragtranslation-rabbitmq
echo  docker start ragtranslation-qdrant
echo.
echo 如需调试某个服务，请使用：docker logs [容器名]
echo =================== 部署检查结束 ===================

pause
endlocal

exit /b 0

:: 备用启动命令示例（如果需要手动操作）：
:: 数据库容器：
:: docker run -d --name ragtranslation-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=guiguxiaozhi mysql:5.7
::  docker run -d --name ragtranslation-redis -p 6379:6379 redis:alpine
::  docker run -d --name ragtranslation-mongo -p 27017:27017 -e MONGO_INITDB_DATABASE=chat_db mongo
::  docker run -d --name ragtranslation-rabbitmq -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=admin rabbitmq:3-management
::  docker run -d --name ragtranslation-qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.8.0