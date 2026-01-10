@echo off
echo ===================================================================
echo 🚀 RAGTranslation Docker 快速部署程序
echo ===================================================================
echo.

:: 检查Docker Desktop
docker version >nul 2>nul
if errorlevel 1 (
    echo 错误: Docker Desktop 未运行或未安装！
    echo 请先启动Docker Desktop，然后重试。
    pause
    exit /b 1
)

echo [1/4] 清理现有容器...
docker rm -f ragtranslation-app 2>nul
docker rm -f ragtranslation-simple 2>nul

echo [2/4] 构建项目镜像...
docker build -t ragtranslation-app:latest .
if errorlevel 1 (
    echo ⚠️ 构建失败，使用现有镜像...
)

echo [3/4] 启动应用服务...
docker run -d --name ragtranslation-app ^
  -p 8000:8000 ^
  -e SPRING_PROFILES_ACTIVE=docker,test ^
  -e DASHSCOPE_API_KEY=test ^
  -e KIMI_API_KEY=test ^
  -e DeepSeek_API_KEY=test ^
  -e BAIDU_MAP_API_KEY=8qM3bsI6oakw1ICy1g1T9Vo0peSP90of ^
  -e SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/chat_db" ^
  ragtranslation-app:latest

echo [4/4] 等待服务启动...
timeout /t 15 /nobreak >nul

echo.
echo ===================================================================
echo 🧪 测试部署结果...
echo ===================================================================

:: 测试HTTP服务
curl.exe -s http://localhost:8000/ > result.html 2>1

if findstr /c:"小智Agent" result.html >nul || findstr /c:"\u5c0f\u667aAgent" result.html >nul (
    echo ✅ 部署成功！
    echo.
    echo 🌐 访问地址：
    echo   - 主要页面：http://localhost:8000/unified.html
    echo   - 应用主页：http://localhost:8000/
    echo   - RabbitMQ管理：http://localhost:15672 (admin/admin)
    echo.
    echo 📱 Docker Desktop:
    echo   - 可观察容器状态和日志
    echo   - 管理端口映射和资源使用
    echo.
) else (
    echo ⚠️ 部署遇到技术问题，查看下面的处理方法
    echo.
    echo 🛠️  建议：
    echo   1. 在Docker Desktop中查看容器日志
    echo   2. 重新启动Docker Desktop并重试
    echo   3. 使用本地运行：mvn spring-boot:run
)

rem 清理临时文件
del result.html 2>nul

echo.
echo ===================================================================
echo 👆 提示：可以在Docker Desktop中观察容器状态
echo 👇 部署已完成，请访问测试URL
echo ===================================================================
pause