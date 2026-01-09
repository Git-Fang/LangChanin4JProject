@echo off
chcp 65001 >nul

echo 正在修复Docker镜像拉取问题...
echo.

REM 检查Docker是否正在运行
 docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误：Docker没有运行，请先启动Docker Desktop
    pause
    exit /b 1
)

echo 1. 配置Docker镜像加速器...
echo.
echo 请打开Docker Desktop设置，添加以下镜像加速器：
echo.
echo 阿里云加速器：https://fvmsjmop.mirror.aliyuncs.com
echo 网易云加速器：https://hub-mirror.c.163.com
echo 腾讯云加速器：https://mirror.ccs.tencentyun.com
echo.
echo 操作步骤：
echo 1. 右键点击Docker Desktop图标，选择 Settings
echo 2. 选择 Docker Engine 选项卡
echo 3. 在JSON配置中添加：
echo    "registry-mirrors": [
echo      "https://fvmsjmop.mirror.aliyuncs.com",
echo      "https://hub-mirror.c.163.com",
echo      "https://mirror.ccs.tencentyun.com"
echo    ]
echo 4. 点击 Apply & Restart
echo.
echo 配置完成后按任意键继续...
pause

echo.
echo 2. 清理Docker缓存和无效镜像...
echo.
docker system prune -a -f
echo.

echo 3. 尝试手动拉取desktop版所需的镜像...
echo.

echo 正在拉取 Ollama 镜像...
docker pull ollama/ollama:latest
if %errorlevel% neq 0 (
    echo Ollama镜像拉取失败，尝试使用备用标签...
    docker pull ollama/ollama:0.3.0
)

echo.
echo 正在拉取 MySQL 镜像...
docker pull mysql:8.0

echo.
echo 正在拉取 MongoDB 镜像...
docker pull mongo:7.0

echo.
echo 正在拉取 Redis 镜像...
docker pull redis/redis-stack:latest

echo.
echo 正在拉取 Qdrant 镜像...
docker pull qdrant/qdrant:latest

echo.
echo 4. 验证镜像拉取状态...
docker images | findstr -E "(ollama|mysql|mongo|redis|qdrant)"
echo.

echo 修复完成！
echo.
echo 现在您可以：
echo 1. 运行 deploy-desktop.bat 重新部署
echo 2. 如果问题仍然存在，请手动重启Docker Desktop后重试
echo.
pause