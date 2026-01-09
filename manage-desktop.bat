@echo off
REM RAG Translation System - Windows管理脚本

set COMPOSE_FILE=docker-compose.desktop.yml

:usage
echo 使用方法: %0 [命令]
echo.
echo 可用命令：
echo   start    - 启动服务
echo   stop     - 停止服务
echo   restart  - 重启服务
echo   logs     - 查看实时日志
echo   status   - 检查服务状态
echo   health   - 健康检查
echo   clean    - 清理所有容器和数据卷
echo   update   - 重新构建并启动服务
echo   shell    - 进入应用容器命令行
goto :eof

:check_docker
docker info 1>nul 2>nul
if errorlevel 1 (
    echo 错误：Docker Desktop未运行！请先启动Docker Desktop
    exit /b 1
)
goto :eof

:start_service
call :check_docker
echo 正在启动服务...
docker-compose -f %COMPOSE_FILE% up -d
if errorlevel 1 (
    echo 错误：服务启动失败！
    exit /b 1
)
echo 服务启动成功！
echo 等待服务初始化...
timeout /t 5 /nobreak > nul
goto :eof

:stop_service
echo 正在停止服务...
docker-compose -f %COMPOSE_FILE% down
echo 服务已停止
goto :eof

:restart_service
echo 正在重启服务...
docker-compose -f %COMPOSE_FILE% restart
echo 等待服务重启...
timeout /t 8 /nobreak > nul
goto :eof

:show_logs
if "%2"=="-f" (
    docker-compose -f %COMPOSE_FILE% logs -f
) else (
    docker-compose -f %COMPOSE_FILE% logs --tail=50
)
goto :eof

:show_status
echo === 服务状态 ===
docker-compose -f %COMPOSE_FILE% ps
exit /b

:check_health
echo 执行健康检查...
docker exec rag-translation curl -s http://localhost:8000/actuator/health
echo.
echo MongoDB状态:
docker exec rag-translation nc -zv rag-mongo 27017 2>&1 && echo MongoDB运行正常 && echo MongoDB连接失败
exit /b

:clean_all
echo === 警告：此操作将删除所有数据和容器！===
set /p confirm=确定要继续吗？(y/N):
if /i "%confirm%"=="Y" (
    echo 正在清理所有资源...
    docker-compose -f %COMPOSE_FILE% down -v --remove-orphans
    docker system prune -f
    echo 清理完成
) else (
    echo 操作已取消
)
goto :eof

:update_service
call :check_docker
echo 正在重新构建和启动...
docker-compose -f %COMPOSE_FILE% build --no-cache
docker-compose -f %COMPOSE_FILE% up -d
goto :eof

:goto_shell
docker exec -it rag-translation /bin/bash
goto :eof

REM 主逻辑
if "%1"=="" goto usage
if "%1"=="start" goto start_service
if "%1"=="stop" goto stop_service
if "%1"=="restart" goto restart_service
if "%1"=="logs" goto show_logs
if "%1"=="status" goto show_status
if "%1"=="health" goto check_health
if "%1"=="clean" goto clean_all
if "%1"=="update" goto update_service
if "%1"=="shell" goto goto_shell
goto usage

goto :eof