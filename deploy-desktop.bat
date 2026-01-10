@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ============================================
echo   RAGTranslation Docker Desktop 部署工具
echo ============================================
echo.

:: 设置变量
set IMAGE_NAME=ragtranslation-app
set CONTAINER_NAME=ragtranslation-app
set APP_PORT=8000

:: 检查Docker Desktop是否运行
echo [1/5] 检查Docker Desktop状态...
docker version >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker Desktop未运行或未安装！
    echo        请先启动Docker Desktop后重试。
    pause
    exit /b 1
)
echo       Docker Desktop运行正常

:: 检查依赖的中间件容器
echo.
echo [2/5] 检查依赖的中间件容器...
set MISSING_SERVICES=

:: 检查MySQL
docker ps --format "{{.Names}}" | findstr /i "mysql" >nul 2>&1
if errorlevel 1 (
    set MISSING_SERVICES=!MISSING_SERVICES! MySQL
) else (
    echo       MySQL: 运行中
)

:: 检查MongoDB
docker ps --format "{{.Names}}" | findstr /i "mongo" >nul 2>&1
if errorlevel 1 (
    set MISSING_SERVICES=!MISSING_SERVICES! MongoDB
) else (
    echo       MongoDB: 运行中
)

:: 检查Redis
docker ps --format "{{.Names}}" | findstr /i "redis" >nul 2>&1
if errorlevel 1 (
    set MISSING_SERVICES=!MISSING_SERVICES! Redis
) else (
    echo       Redis: 运行中
)

:: 检查RabbitMQ
docker ps --format "{{.Names}}" | findstr /i "rabbit" >nul 2>&1
if errorlevel 1 (
    set MISSING_SERVICES=!MISSING_SERVICES! RabbitMQ
) else (
    echo       RabbitMQ: 运行中
)

:: 检查Qdrant
docker ps --format "{{.Names}}" | findstr /i "qdrant" >nul 2>&1
if errorlevel 1 (
    set MISSING_SERVICES=!MISSING_SERVICES! Qdrant
) else (
    echo       Qdrant: 运行中
)

if not "!MISSING_SERVICES!"=="" (
    echo.
    echo [警告] 以下中间件未运行:!MISSING_SERVICES!
    echo        请确保这些服务已在Docker Desktop中启动。
    echo.
    set /p CONTINUE="是否继续部署? (Y/N): "
    if /i not "!CONTINUE!"=="Y" (
        echo 部署已取消。
        pause
        exit /b 1
    )
)

:: 构建Docker镜像
echo.
echo [3/5] 构建应用镜像 (首次构建可能需要几分钟)...
docker build -t %IMAGE_NAME%:latest .
if errorlevel 1 (
    echo [错误] 镜像构建失败！
    pause
    exit /b 1
)
echo       镜像构建成功: %IMAGE_NAME%:latest

:: 停止并删除旧容器
echo.
echo [4/5] 清理旧容器...
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm %CONTAINER_NAME% >nul 2>&1
echo       旧容器已清理

:: 启动新容器
echo.
echo [5/5] 启动应用容器...
docker run -d ^
    --name %CONTAINER_NAME% ^
    -p %APP_PORT%:%APP_PORT% ^
    -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/guiguxiaozhi?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true ^
    -e SPRING_DATASOURCE_USERNAME=root ^
    -e SPRING_DATASOURCE_PASSWORD=root ^
    -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db ^
    -e SPRING_REDIS_HOST=host.docker.internal ^
    -e SPRING_REDIS_PORT=6379 ^
    -e SPRING_RABBITMQ_HOST=host.docker.internal ^
    -e SPRING_RABBITMQ_PORT=5672 ^
    -e SPRING_RABBITMQ_USERNAME=admin ^
    -e SPRING_RABBITMQ_PASSWORD=admin ^
    -e AI_EMBEDDINGSTORE_QDRANT_HOST=host.docker.internal ^
    -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 ^
    -e TZ=Asia/Shanghai ^
    %IMAGE_NAME%:latest

if errorlevel 1 (
    echo [错误] 容器启动失败！
    pause
    exit /b 1
)

:: 等待应用启动
echo       容器已启动，等待应用初始化...
timeout /t 15 /nobreak >nul

:: 显示状态和访问信息
echo.
echo ============================================
echo   部署完成！
echo ============================================
echo.
echo   访问地址:
echo   ----------------------------------------
echo   统一客户端:  http://localhost:%APP_PORT%/unified.html
echo   应用主页:    http://localhost:%APP_PORT%/
echo   API文档:     http://localhost:%APP_PORT%/doc.html
echo   ----------------------------------------
echo.
echo   查看日志命令:  docker logs -f %CONTAINER_NAME%
echo   停止应用命令:  docker stop %CONTAINER_NAME%
echo   重启应用命令:  docker restart %CONTAINER_NAME%
echo.
echo ============================================

:: 尝试打开浏览器
echo 正在打开浏览器...
start http://localhost:%APP_PORT%/unified.html

pause
endlocal
exit /b 0
