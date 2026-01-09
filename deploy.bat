@echo off
REM RAG Translation System - Docker Desktop部署脚本（Windows）
REM 用于将项目快速部署到Docker Desktop并支持外部访问

title RAG Translation System - Docker Desktop部署工具

color 0A

echo.
echo =======================================================
echo        RAG 翻译系统 - Docker Desktop 部署工具
echo =======================================================
echo.

REM 检查Docker Desktop是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker Desktop未运行！请先启动Docker Desktop
    echo.
    pause
    exit /b 1
)

REM 检查Docker Compose可用性
docker compose version >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker Compose未安装或不可用！
    echo.
    pause
    exit /b 1
)

REM 检查.env文件是否存在
if not exist ".env" (
    echo [警告] 未找到.env文件，将使用默认配置
    echo         如需配置API密钥，请复制 .env.example 为 .env
    echo.
)

REM 清理旧容器
echo [信息] 正在清理旧容器...
call :printSeparator
docker compose down --remove-orphans

REM 构建新镜像
echo.
echo [信息] 正在构建Docker镜像...
call :printSeparator
docker compose build --no-cache
if errorlevel 1 (
    echo.
    echo [错误] Docker镜像构建失败！
    pause
    exit /b 1
)

REM 启动服务
echo.
echo [信息] 正在启动服务...
call :printSeparator
docker compose up -d
if errorlevel 1 (
    echo.
    echo [错误] 服务启动失败！
    pause
    exit /b 1
)

REM 等待服务启动完成
echo.
echo [信息] 等待服务完全启动...
call :printSeparator

REM 检查应用服务是否就绪
for /l %%i in (1,1,60) do (
    docker compose ps rag-translation | findstr "healthy" >nul 2>&1
    if !errorlevel! equ 0 (
        echo [成功] 应用服务已就绪！
        goto :startupComplete
    )
    echo [等待] 服务启动中... (%%i/60)
    timeout /t 2 /nobreak >nul
)

:startupComplete
echo.
echo =======================================================
echo                    部署成功！
echo =======================================================
echo.
echo >>= 应用访问地址 =================
echo    Web界面:  http://localhost:8000/index.html
echo    API文档:  http://localhost:8000/doc.html
echo.
echo >>= 管理界面 ====================
echo    RabbitMQ管理界面: http://localhost:15672 (admin/admin123)
echo    RedisInsight:     http://localhost:8001
echo.
echo >>= 服务端口列表 ===============
echo    应用服务:   localhost:8000
echo    MySQL:      localhost:3306
echo    MongoDB:    localhost:27017
echo    Redis:      localhost:6379
echo    RabbitMQ:   localhost:5672
echo    RabbitMQ管理: localhost:15672
echo    Qdrant:     localhost:6333
echo.
echo >>= 管理命令 =====================
echo    查看日志:   docker compose logs -f
echo    停止服务:   docker compose down
echo    查看状态:   docker compose ps
echo    重启服务:   docker compose restart
echo.
echo =======================================================
echo.
echo 按任意键退出...
pause >nul
exit /b 0

:printSeparator
echo -------------------------------------------------------
goto :EOF

REM 错误处理
:ifError
echo.
echo [错误] 发生未知错误！
echo.
pause
exit /b 1