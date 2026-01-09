@echo off
chcp 65001 >nul
title RAG Translation Desktop Deployment
color 0A

setlocal enabledelayedexpansion

:: 设置颜色
set GREEN=92
set YELLOW=93
set RED=91
set BLUE=94
set WHITE=97

:: 打印标题
echo.
call :colorPrint %GREEN% "========================================="
call :colorPrint %GREEN% " RAG Translation System - Docker Desktop "
call :colorPrint %GREEN% "========================================="
echo.

:: 检查Docker环境
call :colorPrint %BLUE% "[1] 检查Docker环境..."
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    call :colorPrint %RED% "[✗] Docker未安装或未运行"
    pause
    exit /b 1
)
call :colorPrint %GREEN% "[✓] Docker已安装并运行"
echo.

:: 检查Docker Compose
call :colorPrint %BLUE% "[2] 检查Docker Compose..."
docker compose version >nul 2>&1
if %errorlevel% equ 0 (
    set COMPOSE_CMD=docker compose
) else (
    docker-compose --version >nul 2>&1
    if %errorlevel% equ 0 (
        set COMPOSE_CMD=docker-compose
    ) else (
        call :colorPrint %RED% "[✗] Docker Compose未安装"
        pause
        exit /b 1
    )
)
call :colorPrint %GREEN% "[✓] Docker Compose已就绪"
echo.

:: 定义所需镜像
set IMAGES=mysql:8.0 mongo:7.0 redis/redis-stack:latest rabbitmq:3-management qdrant/qdrant:latest
call :colorPrint %BLUE% "[3] 检查本地镜像..."
set /a MISSING_COUNT=0

for %%i in (%IMAGES%) do (
    docker image inspect %%i >nul 2>&1
    if !errorlevel! equ 0 (
        call :colorPrint %GREEN% "[✓] %%i 本地镜像存在"
    ) else (
        call :colorPrint %YELLOW% "[→] %%i 本地镜像不存在"
        echo     将在启动时自动拉取
        set /a MISSING_COUNT+=1
    )
)
echo.

:: 检查项目文件
call :colorPrint %BLUE% "[4] 检查项目文件..."
if not exist "docker-compose-desktop.yml" (
    call :colorPrint %RED% "[✗] docker-compose-desktop.yml 不存在"
    pause
    exit /b 1
)
call :colorPrint %GREEN% "[✓] 项目文件完整"
echo.

:: 设置端口占用检查
call :colorPrint %BLUE% "[5] 检查端口占用..."
set PORTS=3306 27017 6379 5672 15672 6333 6334 8000
for %%p in (%PORTS%) do (
    netstat -ano | findstr ":%%p" >nul 2>&1
    if !errorlevel! equ 0 (
        call :colorPrint %YELLOW% "[!] 端口 %%p 可能被占用，请确认"
    )
)
call :colorPrint %GREEN% "[✓] 端口检查完成"
echo.

:: 构建主应用镜像
call :colorPrint %BLUE% "[6] 构建RAG Translation应用镜像..."
cd /d "%~dp0"
call :colorPrint %WHITE% "    执行: docker build -t rag-translation:desktop ."
docker build -t rag-translation:desktop . > build.log 2>&1
if %errorlevel% neq 0 (
    call :colorPrint %RED% "[✗] 构建失败"
    echo     查看 build.log 获取详细信息
    pause
    exit /b 1
)
call :colorPrint %GREEN% "[✓] 构建完成"
echo.

:: 创建必要的目录
if not exist "logs" mkdir logs
if not exist "uploads" mkdir uploads
if not exist "backups" mkdir backups
if not exist "sql" mkdir sql

:: 如果init.sql不存在，创建一个基础初始化脚本
if not exist "sql\init.sql" (
echo -- RAG Translation Database Initialization > sql\init.sql
echo CREATE DATABASE IF NOT EXISTS rag_translation CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; >> sql\init.sql
echo USE rag_translation; >> sql\init.sql
echo SELECT 'Database initialized' AS status; >> sql\init.sql
)

:: 停止并移除旧的容器
call :colorPrint %BLUE% "[7] 清理旧容器..."
%COMPOSE_CMD% -f docker-compose-desktop.yml down >nul 2>&1

:: 启动服务
echo.
call :colorPrint %GREEN% "========================================="
call :colorPrint %GREEN% "正在启动服务..."
call :colorPrint %GREEN% "========================================="
echo.

%COMPOSE_CMD% -f docker-compose-desktop.yml up -d
if %errorlevel% neq 0 (
    call :colorPrint %RED% "[✗] 启动失败"
    echo.
    echo 请检查错误信息，可以执行以下命令查看日志：
    echo   %COMPOSE_CMD% -f docker-compose-desktop.yml logs
    pause
    exit /b 1
)
echo.

echo.
call :colorPrint %GREEN% "========================================="
call :colorPrint %GREEN% "服务启动成功！"
call :colorPrint %GREEN% "========================================="
echo.
call :colorPrint %WHITE% "访问地址："
call :colorPrint %WHITE% "  主页:    http://localhost:8000/unified.html"
call :colorPrint %WHITE% "  API文档: http://localhost:8000/doc.html"
echo.
call :colorPrint %WHITE% "管理界面："
call :colorPrint %WHITE% "  RabbitMQ: http://localhost:15672 (admin/admin123)"
echo.
call :colorPrint %WHITE% "查看日志："
call :colorPrint %WHITE% "  应用日志: %COMPOSE_CMD% -f docker-compose-desktop.yml logs -f rag-translation"
call :colorPrint %WHITE% "  全部日志: %COMPOSE_CMD% -f docker-compose-desktop.yml logs -f"
echo.
call :colorPrint %WHITE% "常用命令："
call :colorPrint %WHITE% "  停止服务: %COMPOSE_CMD% -f docker-compose-desktop.yml stop"
call :colorPrint %WHITE% "  重启服务: %COMPOSE_CMD% -f docker-compose-desktop.yml restart"
call :colorPrint %WHITE% "  删除容器: %COMPOSE_CMD% -f docker-compose-desktop.yml down"
echo.
echo.
pause
exit /b 0

:: 颜色打印函数（简化版，减少依赖）
:colorPrint
echo %~2
exit /b