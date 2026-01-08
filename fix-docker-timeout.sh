#!/bin/bash

echo "=== Docker 强制清理脚本 ==="
echo ""

echo "步骤1：停止所有相关容器..."
# 强制停止，只给5秒宽限期
docker stop -t 5 rag-translation rag-mongo mongodb 2>/dev/null || true
echo "✓ 容器已停止"

echo ""
echo "步骤2：强制删除容器..."
docker rm -f rag-translation rag-mongo mongodb 2>/dev/null || true
echo "✓ 容器已删除"

echo ""
echo "步骤3：清理Docker网络..."
# 删除项目网络
docker network rm langchanin4jproject_app-network 2>/dev/null || true
docker network rm rag-translation_app-network 2>/dev/null || true
# 清理孤儿网络
docker network prune -f
echo "✓ 网络已清理"

echo ""
echo "步骤4：检查Docker守护进程状态..."
sudo systemctl status docker --no-pager | head -3

echo ""
echo "=== 清理完成 ==="
echo ""
echo "现在可以重新启动服务："
echo "  export COMPOSE_HTTP_TIMEOUT=600"
echo "  export DOCKER_CLIENT_TIMEOUT=600"
echo "  docker-compose up -d"
echo ""
echo "或使用部署脚本："
echo "  ./deploy.sh"
