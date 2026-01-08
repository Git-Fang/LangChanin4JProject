#!/bin/bash

# 清理脚本 - 停止并删除旧的独立MongoDB容器

echo "=== 清理旧容器 ==="

# 停止并删除旧的MongoDB容器
if docker ps -a | grep -q "mongodb"; then
    echo "发现旧的MongoDB容器，正在删除..."
    docker stop mongodb 2>/dev/null || true
    docker rm mongodb 2>/dev/null || true
    echo "✓ 旧MongoDB容器已删除"
else
    echo "未发现旧MongoDB容器"
fi

# 停止并删除docker-compose管理的容器
echo "停止docker-compose服务..."
docker-compose down -v

echo ""
echo "=== 清理完成 ==="
echo "现在可以运行 ./deploy.sh 或 ./start.sh 重新部署"
