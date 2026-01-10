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
echo [1/6] 检查Docker Desktop状态...
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
echo [2/6] 检查依赖的中间件容器...
set MISSING_SERVICES=

docker ps --format "{{.Names}}" | findstr /i "mysql" >nul 2>&1
if errorlevel 1 (set MISSING_SERVICES=!MISSING_SERVICES! MySQL) else (echo       MySQL: 运行中)

docker ps --format "{{.Names}}" | findstr /i "mongo" >nul 2>&1
if errorlevel 1 (set MISSING_SERVICES=!MISSING_SERVICES! MongoDB) else (echo       MongoDB: 运行中)

docker ps --format "{{.Names}}" | findstr /i "qdrant" >nul 2>&1
if errorlevel 1 (set MISSING_SERVICES=!MISSING_SERVICES! Qdrant) else (echo       Qdrant: 运行中)

if not "!MISSING_SERVICES!"=="" (
    echo.
    echo [警告] 以下中间件未运行:!MISSING_SERVICES!
    set /p CONTINUE="是否继续部署? (Y/N): "
    if /i not "!CONTINUE!"=="Y" (
        echo 部署已取消。
        pause
        exit /b 1
    )
)

:: 检查基础镜像
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

:: Maven构建JAR包
echo.
echo [4/6] 构建Java应用...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [错误] Maven构建失败！
    pause
    exit /b 1
)
echo       Java应用构建成功

:: 构建Docker镜像
echo.
echo [5/6] 构建Docker镜像...
docker build -t %IMAGE_NAME%:latest .
if errorlevel 1 (
    echo [错误] Docker镜像构建失败！
    pause
    exit /b 1
)
echo       镜像构建成功: %IMAGE_NAME%:latest

:: 停止并删除旧容器
echo.
echo       清理旧容器...
docker stop %CONTAINER_NAME% >nul 2>&1
docker rm -f %CONTAINER_NAME% >nul 2>&1
timeout /t 3 /nobreak >nul

:: 启动新容器
echo.
echo [6/6] 启动应用容器...

:: 读取.env文件中的API密钥
for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    if not "%%a"=="" if not "%%a:~0,1%"=="#" (
        set "%%a=%%b"
    )
)

docker run -d ^
    --name %CONTAINER_NAME% ^
    -p %APP_PORT%:%APP_PORT% ^
    -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/guiguxiaozhi?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true ^
    -e SPRING_DATASOURCE_USERNAME=root ^
    -e SPRING_DATASOURCE_PASSWORD=root ^
    -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db ^
    -e AI_EMBEDDINGSTORE_QDRANT_HOST=host.docker.internal ^
    -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 ^
    -e DeepSeek_API_KEY=%DeepSeek_API_KEY% ^
    -e KIMI_API_KEY=%KIMI_API_KEY% ^
    -e DASHSCOPE_API_KEY=%DASHSCOPE_API_KEY% ^
    -e BAIDU_MAP_API_KEY=%BAIDU_MAP_API_KEY% ^
    -e TZ=Asia/Shanghai ^
    %IMAGE_NAME%:latest

if errorlevel 1 (
    echo [错误] 容器启动失败！
    pause
    exit /b 1
)

echo       容器已启动，等待应用初始化...
timeout /t 20 /nobreak >nul

:: 显示结果
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
echo   常用命令:
echo   查看日志:  docker logs -f %CONTAINER_NAME%
echo   停止应用:  docker stop %CONTAINER_NAME%
echo   重启应用:  docker restart %CONTAINER_NAME%
echo.
echo ============================================

echo 正在打开浏览器...
start http://localhost:%APP_PORT%/unified.html

pause
endlocal
exit /b 0
