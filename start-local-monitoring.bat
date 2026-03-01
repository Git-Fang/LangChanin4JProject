@echo off
echo ============================================
echo   Local Development Monitoring Services
echo ============================================
echo.

echo [1/4] Starting Prometheus...
docker rm -f prometheus-local >nul 2>&1
docker run -d --name prometheus-local --network ai-network -p 9090:9090 ^
    -v %CD%/prometheus-local.yml:/etc/prometheus/prometheus.yml:ro ^
    prom/prometheus:v2.47.0
if errorlevel 1 (
    echo [WARNING] Prometheus failed to start
) else (
    echo       Prometheus started on http://localhost:9090
)

echo.
echo [2/4] Starting Grafana...
docker rm -f grafana-local >nul 2>&1
docker run -d --name grafana-local --network ai-network -p 3000:3000 ^
    -e GF_SECURITY_ADMIN_USER=admin ^
    -e GF_SECURITY_ADMIN_PASSWORD=admin123 ^
    -e TZ=Asia/Shanghai ^
    -v grafana-data:/var/lib/grafana ^
    -v %CD%/monitoring/grafana-provisioning/dashboards:/etc/grafana/provisioning/dashboards:ro ^
    -v %CD%/monitoring/grafana-provisioning/datasources:/etc/grafana/provisioning/datasources:ro ^
    grafana/grafana:10.1.10
if errorlevel 1 (
    echo [WARNING] Grafana failed to start
) else (
    echo       Grafana started on http://localhost:3000 (admin/admin123)
)

echo.
echo [3/4] Starting MongoDB Exporter (for local MongoDB)...
docker rm -f mongodb-exporter-local >nul 2>&1
docker run -d --name mongodb-exporter-local -p 9216:9216 ^
    -e MONGODB_URI=mongodb://host.docker.internal:27017 ^
    percona/mongodb_exporter:0.39.0 ^
    --mongodb.uri=mongodb://host.docker.internal:27017
if errorlevel 1 (
    echo [WARNING] MongoDB Exporter failed to start
) else (
    echo       MongoDB Exporter started on http://localhost:9216
)

echo.
echo [4/4] Starting Redis Exporter (for local Redis)...
docker rm -f redis-exporter-local >nul 2>&1
docker run -d --name redis-exporter-local -p 9121:9121 ^
    -e REDIS_ADDR=redis://host.docker.internal:6379 ^
    oliver006/redis_exporter:latest
if errorlevel 1 (
    echo [WARNING] Redis Exporter failed to start
) else (
    echo       Redis Exporter started on http://localhost:9121
)

echo.
echo ============================================
echo   Monitoring Services Started
echo ============================================
echo.
echo   URLs:
echo   ----------------------------------------
echo   Grafana:     http://localhost:3000
echo   Prometheus:  http://localhost:9090
echo   ----------------------------------------
echo.
echo   To stop: docker stop prometheus-local grafana-local mongodb-exporter-local redis-exporter-local
echo   To remove: docker rm -f prometheus-local grafana-local mongodb-exporter-local redis-exporter-local
echo ============================================
echo.
pause
