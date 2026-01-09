@echo off
REM RAG Translation System - Docker Desktop部署脚本（Windows）

echo === RAG翻译系统 Windows Docker Desktop 部署脚本 ===
echo.

REM 检查Docker Desktop是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo 错误：Docker Desktop未运行！请先启动Docker Desktop
    pause
    exit /b 1
)

REM 检查docker-compose可用性
docker-compose version >nul 2>&1
if errorlevel 1 (
    echo 错误：Docker Compose未安装！
    pause
    exit /b 1
)

REM 检查.env文件
if not exist ".env" (
    echo 警告：未找到.env文件，将使用默认配置
    echo.
)

REM 清理旧容器
echo 正在清理旧容器...
docker-compose -f docker-compose.desktop.yml down
echo.

REM 构建镜像
echo 正在构建Docker镜像...
docker-compose -f docker-compose.desktop.yml build
if errorlevel 1 (
    echo 错误：Docker镜像构建失败！
    pause
    exit /b 1
)
echo.

REM 启动服务
echo 正在启动服务...
docker-compose -f docker-compose.desktop.yml up -d
if errorlevel 1 (
    echo 错误：服务启动失败！
    pause
    exit /b 1
)
echo.

REM 等待服务启动
echo 等待服务完全启动...
timeout /t 10 /nobreak >nul

echo.
echo === 部署成功！===
echo.
echo 应用访问地址： http://localhost:8000/index.html
echo API文档地址：   http://localhost:8000/doc.html
echo.
echo 服务端口：
echo   - 应用端口：   localhost:8000
echo   - MySQL：     localhost:3306
echo   - MongoDB：   localhost:27017
echo   - Redis：     localhost:6379
echo   - Qdrant：    localhost:6333
echo   - Ollama：    localhost:11434
echo.
echo 常用命令：
echo   - 查看日志：   docker-compose -f docker-compose.desktop.yml logs -f
echo   - 停止服务：   docker-compose -f docker-compose.desktop.yml down
echo   - 查看状态：   docker-compose -f docker-compose.desktop.yml ps
echo.
echo.
pause