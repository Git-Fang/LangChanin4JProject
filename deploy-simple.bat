@echo off
title RAG Translation Quick Deploy
color 0A

echo.
echo =========================================
echo  RAG Translation Quick Deploy for Desktop
echo =========================================
echo.
echo 注意：此脚本使用简化配置快速启动RAG翻译系统
echo.

:: 检查Docker环境
echo [1] 检查Docker环境...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] Docker未安装或未运行
    echo 请先启动Docker Desktop
    pause
    exit /b 1
)
echo [✓] Docker已就绪
echo.

:: 选择部署模式
echo [2] 选择部署模式：
echo    1. 标准模式（需要完整环境）
echo    2. 简化模式（仅核心功能，推荐）
echo.
set /p MODE=请选择模式（1或2）：
if "%MODE%"=="1" (
    set COMPOSE_FILE=docker-compose-desktop.yml
    set PROFILE=标准模式
) else (
    set COMPOSE_FILE=docker-compose-demo.yml
    set PROFILE=简化模式
)
echo [+] 已选择：%PROFILE%
echo.

:: 检查项目文件
echo [3] 检查项目文件...
if not exist "%COMPOSE_FILE%" (
    echo [错误] %COMPOSE_FILE% 不存在
    pause
    exit /b 1
)
echo [✓] 项目文件就绪
echo.

:: 检查JAR文件
echo [4] 检查应用文件...
if not exist "target\*.jar" (
    echo [警告] 未发现JAR文件
    echo [提示] 请先运行：mvn clean package -DskipTests
    pause
    exit /b 1
)
echo [✓] 应用文件就绪
echo.

:: 创建目录
echo [5] 创建必要目录...
if not exist "logs" mkdir logs
if not exist "uploads" mkdir uploads
if not exist "backups" mkdir backups
echo [✓] 目录创建完成
echo.

:: 停止旧容器
echo [6] 停止旧容器...
call docker compose -f %COMPOSE_FILE% down >nul 2>&1
echo [✓] 旧容器已停止
echo.

:: 启动服务
echo =========================================
echo 正在启动服务...这可能需要几分钟
echo =========================================
echo.

:: 尝试启动-echo 模式
call docker compose -f %COMPOSE_FILE% up -d
if %errorlevel% neq 0 (
    echo [错误] 服务启动失败
    echo.
    echo 可能的解决方案：
    echo   1. 检查端口占用：docker port %COMPOSE_FILE%
    echo   2. 查看日志：docker compose -f %COMPOSE_FILE% logs
    echo   3. 重启Docker Desktop
    echo   4. 使用另一个模式（1或2）
    pause
    exit /b 1
)

:: 检查服务状态
echo.
echo [7] 等待服务就绪...
timeout /t 10 /nobreak >nul

:: 最终状态
echo.
echo =========================================
echo 部署完成！
echo =========================================
echo.
echo [访问地址]
echo   主页（unified.html）: http://localhost:8000/unified.html
echo   API文档: http://localhost:8000/doc.html
echo.
echo [管理界面]
echo   RabbitMQ管理: http://localhost:15672 (admin/admin123)
echo.
echo [实用命令]
echo   查看日志: docker compose -f %COMPOSE_FILE% logs -f
echo   停止服务: docker compose -f %COMPOSE_FILE% down
echo   重启服务: docker compose -f %COMPOSE_FILE% restart
echo.
echo =========================================
echo.
echo 请访问 http://localhost:8000/unified.html 开始使用
echo.
echo.
pause