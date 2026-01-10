@echo off
echo 🚀 快速Docker测试运行

:: 检查环境
docker version >nul 2>nul
if errorlevel 1 (
    echo 错误: Docker Desktop未运行
    echo 请先启动Docker Desktop
    pause
    exit /b 1
)

echo 使用现有镜像运行测试容器...
docker rm -f ragtranslation-app >nul 2>&1

:: 一键运行 - 跳过AI模型
docker run -d --name ragtranslation-app ^
  -p 8000:8000 ^
  -e SPRING_PROFILES_ACTIVE=docker,docker-bypass ^
  -e SPRING_MAIN_LAZY_INITIALIZATION=true ^
  -e SPRING_DATASOURCE_URL="jdbc:h2:mem:testdb" ^
  -e SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/chat_db" ^
  -e DASHSCOPE_API_KEY=test ^
  -e KIMI_API_KEY=test ^
  -e DeepSeek_API_KEY=test ^
  -e BAIDU_MAP_API_KEY=8qM3bsI6oakw1ICy1g1T9Vo0peSP90of ^
  ragtranslation--docker-app:latest

echo ⏳ 等待服务启动...
ping -n 11 127.0.0.1 >nul 2>nul

echo.
echo ✅ Docker容器运行中！
echo 📍访问地址：http://localhost:8000/unified.html
echo 📊Docker Desktop：查看运行状态
echo 📝查看日志：docker logs ragtranslation-app
echo.
echo 提示：
echo - 主页面：http://localhost:8000/
echo - 静态页面：http://localhost:8000/unified.html
echo - （建议使用浏览器访问，确保页面加载完整）

start http://localhost:8000/unified.html
pause