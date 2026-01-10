@echo off
echo.
echo 正在停止RAGTranslation Docker服务...
echo.

:: 检查docker-compose文件是否存在
if not exist docker-compose.yml (
    echo 错误: 未找到docker-compose.yml文件
    echo 请确保在正确的目录下运行此脚本
    pause
    exit /b 1
)

:: 显示当前运行的容器
echo 当前运行的容器:
docker-compose ps
echo.

:: 停止并移除容器
echo 正在移除所有相关容器和卷...
choice /C YN /M "确定要停止并移除所有容器和卷吗？数据将被删除！"
if errorlevel 2 (
    echo 操作已取消
    pause
    exit /b 0
)

:: 停止并移除容器、网络和卷
docker-compose down -v
if errorlevel 1 (
    echo 错误: 停止服务失败
    pause
    exit /b 1
)

echo.
echo 服务已完全停止并移除！
echo.
echo 相关数据卷已删除。如需保留数据，请在下次启动前备份 volumes 目录
echo.
pause

endlocal
exit /b 0