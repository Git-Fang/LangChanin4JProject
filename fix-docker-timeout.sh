#!/bin/bash

echo "=== Docker 超时问题修复脚本 ==="
echo ""

# 1. 设置更长的超时时间
export COMPOSE_HTTP_TIMEOUT=300
export DOCKER_CLIENT_TIMEOUT=300

echo "✓ 已设置超时时间为 300 秒"
echo ""

# 2. 强制停止所有相关容器
echo "步骤1：强制停止容器..."
docker stop rag-translation rag-mongo 2>/dev/null || true
sleep 2

echo "步骤2：强制删除容器..."
docker rm -f rag-translation rag-mongo 2>/dev/null || true
sleep 2

# 3. 清理网络和卷
echo "步骤3：清理Docker资源..."
docker network prune -f 2>/dev/null || true
docker volume prune -f 2>/dev/null || true

echo ""
echo "=== 清理完成 ==="
echo ""
echo "现在可以重新启动服务："
echo "  docker-compose build"
echo "  docker-compose up -d"
