@echo off
setlocal enabledelayedexpansion
echo.
echo 正在检查RAGTranslation服务状态...

:: 检查容器状态
echo.
echo.=================== ===================
echo 容器状态:
echo.=================== ===================
docker-compose ps
echo.=================== ===================

echo.
echo 端口监听状态:
echo   MySQL(3306)    : netstat -an | find "3306"
echo   Redis(6379)    : netstat -an | find "6379"
echo   RabbitMQ(5672) : netstat -an | find "5672"
echo   RabbitMQ管理(15672): netstat -an | find "15672"
echo   MongoDB(27017) : netstat -an | find "27017"
echo   Qdrant(6333)   : netstat -an | find "6333"
echo   Qdrant(6334)   : netstat -an | find "6334"
echo   应用(8000)     : netstat -an | find "8000"
echo.

echo 服务健康检查:
echo.

:: 检查MySQL
powershell -Command "try { (Invoke-WebRequest -Uri 'http://localhost:3306' -TimeoutSec 1 -UseBasicParsing).StatusCode; Write-Host ' ✅ MySQL端口正常监听' } catch { Write-Host ' ⏳ MySQL可能还在启动中' }"
:: 检查Redis
powershell -Command "try { (Invoke-WebRequest -Uri 'http://localhost:6379' -TimeoutSec 1 -UseBasicParsing).StatusCode; Write-Host ' ✅ Redis端口正常监听' } catch { Write-Host ' ⏳ Redis可能还在启动中' }"
:: 检查RabbitMQ管理界面
powershell -Command "try { (Invoke-WebRequest -Uri 'http://localhost:15672' -TimeoutSec 2 -UseBasicParsing).StatusCode; Write-Host ' ✅ RabbitMQ管理界面正常' } catch { Write-Host ' ⏳ RabbitMQ管理界面未就绪' }"
:: 检查MongoDB
powershell -Command "try { (Invoke-WebRequest -Uri 'http://localhost:27017' -TimeoutSec 1 -UseBasicParsing).StatusCode; Write-Host ' ✅ MongoDB端口正常监听' } catch { Write-Host ' ⏳ MongoDB可能还在启动中' }"
:: 检查Qdrant
powershell -Command "try { (Invoke-WebRequest -Uri 'http://localhost:6333/health' -TimeoutSec 3 -UseBasicParsing).StatusCode; Write-Host ' ✅ Qdrant服务正常' } catch { Write-Host ' ⏳ Qdrant服务未就绪' }"
:: 检查应用服务
powershell -Command "try { (Invoke-WebRequest -Uri 'http://localhost:8000/actuator/health' -TimeoutSec 3 -UseBasicParsing).StatusCode; Write-Host ' ✅ 应用服务正常' } catch { Write-Host ' ⏳ 应用服务未就绪' }"

echo.
echo 应用访问测试:
echo   - 主页:     http://localhost:8000/
echo   - 统一页面: http://localhost:8000/unified.html

echo.
docker-compose top app 2>nul | findstr java >nul
if errorlevel 1 (
    echo 应用进程未检测到
) else (
    echo 应用进程运行正常
)

endlocal
pause