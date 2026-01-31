@echo off
echo ============================================
echo   Stopping Local Monitoring Services
echo ============================================
echo.

echo Stopping Prometheus...
docker stop prometheus-local >nul 2>&1 && echo       Prometheus stopped || echo       Prometheus not running

echo Stopping Grafana...
docker stop grafana-local >nul 2>&1 && echo       Grafana stopped || echo       Grafana not running

echo Stopping MongoDB Exporter...
docker stop mongodb-exporter-local >nul 2>&1 && echo       MongoDB Exporter stopped || echo       MongoDB Exporter not running

echo Stopping Redis Exporter...
docker stop redis-exporter-local >nul 2>&1 && echo       Redis Exporter stopped || echo       Redis Exporter not running

echo.
echo ============================================
echo   All monitoring services stopped
echo ============================================
echo.
pause
