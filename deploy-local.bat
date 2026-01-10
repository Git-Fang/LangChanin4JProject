@echo off
title RAG Translation Local Deployment
color 2A

echo.
echo =========================================
echo  RAG Translation Local Deploy (JAR Direct)
echo =========================================
echo.
echo 注意：此脚本将直接运行JAR文件，不使用Docker
echo.

:: 检查Java环境
echo [1] 检查Java环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] Java未安装或未配置
    echo 请先安装Java 17或更高版本
    pause
    exit /b 1
)
echo [✓] Java已就绪
echo.

:: 检查JAR文件
echo [2] 检查应用文件...
if not exist "target\*.jar" (
    echo [错误] 未发现JAR文件
    echo [提示] 请先运行：mvn clean package -DskipTests
    pause
    exit /b 1
)
echo [✓] 应用文件就绪
echo.

:: 检查数据库连接（MongoDB检查）
echo [3] 检查MongoDB连接...
netstat -ano | findstr ":27017" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] MongoDB在本地运行中
) else (
    echo [警告] MongoDB未检测到
    echo [提示] 可以运行：docker run -d -p 27017:27017 --name mongo mongo:7.0
)
echo.

echo [4] 检查MySQL连接...
netstat -ano | findstr ":3306" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] MySQL在本地运行中
) else (
    echo [警告] MySQL未检测到
    echo [提示] 可以运行：docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root --name mysql mysql:8.0
)
echo.

:: 创建目录
echo [5] 创建必要目录...
if not exist "logs" mkdir logs
if not exist "uploads" mkdir uploads
echo [✓] 目录创建完成
echo.

:: 启动应用
echo =========================================
echo 正在启动RAG翻译系统...
echo =========================================
echo.

:: 设置Java参数
set JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC
set SERVER_OPTS=-Dserver.port=8000 -Dserver.address=0.0.0.0

:: 启动应用
cd /d "%~dp0"
echo [+] 使用配置：
echo.

:: Choose port
echo [6] 选择服务端口：
echo    默认端口 (8000)
echo    如果你在开发其他项目，可以选择其他端口
echo.
set /p PORT=请输入端口号（默认8000）：
if "%PORT%"=="" (
    set PORT=8000
)
echo.
echo 正在启动应用...端口：%PORT%+-echo

:: 启动Java应用
echo.
echo [+] Starting RAG Translation System...
echo =========================================
echo.
echo 访问地址：http://localhost:%PORT%/unified.html
echo.
echo 按 Ctrl+C 停止服务
echo.
echo =========================================
echo.

java %JAVA_OPTS% %SERVER_OPTS% -Dserver.port=%PORT% -jar target/*.jar