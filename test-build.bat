@echo off
REM RAG Translation System - Docker Desktop 构建测试脚本（Windows）

echo === RAG Translation System - Docker Desktop 构建测试 ===
echo.

REM 检查Docker是否运行
docker info 1>nul 2>nul
if errorlevel 1 (
    echo 错误：Docker Desktop 未运行！请先启动 Docker Desktop
    pause
    exit /b 1
)

echo [1/5] 验证 Docker Compose 配置...
docker-compose -f docker-compose.desktop.yml config > nul 2>&1
if errorlevel 1 (
    echo 错误：Docker Compose 配置无效
    pause
    exit /b 1
) else (
    echo Docker Compose 配置有效
)

echo.
echo [2/5] 测试构建应用镜像...
docker build -f Dockerfile.desktop -t rag-translation:test . 2>&log.txt

if errorlevel 1 (
    echo 错误：应用镜像构建失败
    echo 错误日志：
    type log.txt | findstr /i "error"
    del log.txt
    pause
    exit /b 1
) else (
    echo 应用镜像构建成功
    del log.txt
)

echo.
echo [3/5] 验证镜像...
echo 镜像信息：
docker images rag-translation:test --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

echo.
echo [4/5] 测试镜像启动...
echo 启动测试容器...
docker run -d --name test-rag-translation ^
  -p 10800:8000 ^
  -e DeepSeek_API_KEY=test-key ^
  -e KIMI_API_KEY=test-key ^
  -e DASHSCOPE_API_KEY=test-key ^
  rag-translation:test

echo 等待启动...
timeout /t 10 /nobreak > nul

REM 检查状态
echo 检查状态...
docker ps --format "table {{.Names}}\t{{.Status}}" | findstr "test-rag-translation"
if errorlevel 1 (
    echo 错误：容器启动失败
    echo 最后20行日志：
    docker logs test-rag-translation --tail=20
) else (
    echo 容器启动成功
)

REM 测试端口
echo 测试端口...
curl -f http://localhost:10800/actuator/health > nul 2>&1
if errorlevel 1 (
    echo 警告：应用服务端口未响应（测试环境无数据库连接）
) else (
    echo 应用服务响应正常
)

echo.
echo [5/5] 清理测试容器...
docker stop test-rag-translation > nul 2>&1
docker rm test-rag-translation > nul 2>&1
docker rmi rag-translation:test > nul 2>&1

echo.
echo === 构建测试完成 ===
echo.
echo 下一步：
echo 1. 配置有效的 API Keys 到 .env 文件
echo 2. 运行部署脚本：
echo    Linux/MacOS: ./deploy-desktop.sh
echo    Windows: deploy-desktop.bat
echo.
echo 注意：由于需要下载各种基础镜像，首次部署可能需要较长时间
echo 建议保持网络畅通，耐心等待下载完成
pause