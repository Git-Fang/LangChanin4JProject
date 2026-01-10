@echo off
title RAG Translation Desktop Deployment
color 0A

echo.
echo =========================================
echo  RAG Translation System - Docker Desktop
echo =========================================
echo.
echo 注意：此脚本将部署RAG翻译系统到Docker Desktop
echo 确保已安装Docker Desktop并正在运行
echo.

:: 检查Docker环境
echo [1] 检查Docker环境...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] Docker未安装或未运行
    echo 请确保Docker Desktop已安装并正在运行
    pause
    exit /b 1
)
echo [✓] Docker已安装并运行
echo.

:: 检查Docker Compose
echo [2] 检查Docker Compose...
docker compose version >nul 2>&1
if %errorlevel% equ 0 (
    set COMPOSE_CMD=docker compose
) else (
    docker-compose --version >nul 2>&1
    if %errorlevel% equ 0 (
        set COMPOSE_CMD=docker-compose
    ) else (
        echo [错误] Docker Compose未安装
        pause
        exit /b 1
    )
)
echo [✓] Docker Compose已就绪
echo.

:: 检查项目文件
echo [3] 检查项目文件...
if not exist "docker-compose-desktop.yml" (
    echo [错误] docker-compose-desktop.yml 不存在
    pause
    exit /b 1
)
if not exist "Dockerfile" (
    echo [错误] Dockerfile 不存在
    pause
    exit /b 1
)
echo [✓] 项目文件完整
echo.

:: 检查依赖镜像（简略版）
echo [4] 检查依赖镜像...
echo  将检查并拉取以下镜像（如本地不存在）：
echo   - mysql:8.0
echo   - mongo:7.0
echo   - redis/redis-stack:latest
echo   - rabbitmq:3-management
echo   - qdrant/qdrant:latest
echo.
:: 创建必要的目录
echo [5] 创建必要的目录...
if not exist "logs" mkdir logs
if not exist "uploads" mkdir uploads
if not exist "backups" mkdir backups
if not exist "sql" mkdir sql
echo [✓] 目录创建完成
echo.

:: 如果init.sql不存在，创建一个基础初始化脚本
if not exist "sql\init.sql" (
echo -- RAG Translation Database Initialization > sql\init.sql
echo CREATE DATABASE IF NOT EXISTS rag_translation CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; >> sql\init.sql
echo USE rag_translation; >> sql\init.sql
echo SELECT 'Database initialized' AS status; >> sql\init.sql
)

:: 停止并移除旧的容器
echo [6] 清理旧容器...
%COMPOSE_CMD% -f docker-compose-desktop.yml down >nul 2>&1
echo [✓] 清理完成
echo.

:: 构建主应用镜像
echo [7] 构建RAG Translation应用镜像...
cd /d "%~dp0"
echo  正在构建，这可能需要几分钟时间...
docker build -t rag-translation:desktop . > build.log 2>&1
if %errorlevel% neq 0 (
    echo [错误] 构建失败
    echo [提示] 请查看 build.log 获取详细信息
    pause
    exit /b 1
)
echo [✓] 构建完成
echo.

:: 启动服务
echo =========================================
echo 正在启动服务...
echo =========================================
echo.

%COMPOSE_CMD% -f docker-compose-desktop.yml up -d
if %errorlevel% neq 0 (
    echo [错误] 启动失败
    echo.
    echo 提示：
    echo   - 检查端口是否被占用：3306, 27017, 6379, 5672, 6333, 8000
    echo   - 查看日志：%COMPOSE_CMD% -f docker-compose-desktop.yml logs
    pause
    exit /b 1
)
echo.
echo [✓] 服务启动成功！
echo.
echo =========================================
echo 服务访问信息：
echo =========================================
echo.
echo [应用访问]
echo   主页:    http://localhost:8000/unified.html
echo   API文档: http://localhost:8000/doc.html
echo.
echo [数据库管理]
echo   RabbitMQ: http://localhost:15672 (admin/admin123)
echo.
echo [查看日志]
echo   应用日志: %COMPOSE_CMD% -f docker-compose-desktop.yml logs -f rag-translation
echo   全部日志: %COMPOSE_CMD% -f docker-compose-desktop.yml logs -f
echo.
echo [常用命令]
echo   停止服务: %COMPOSE_CMD% -f docker-compose-desktop.yml stop
echo   重启服务: %COMPOSE_CMD% -f docker-compose-desktop.yml restart
echo   删除容器: %COMPOSE_CMD% -f docker-compose-desktop.yml down
echo.
echo =========================================
echo.
echo 部署完成！请访问 http://localhost:8000/unified.html
echo.
pause
exit /b 0