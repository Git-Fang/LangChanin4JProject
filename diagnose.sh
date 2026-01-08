#!/bin/bash

echo "=== 应用健康诊断 ==="
echo ""

echo "1. 检查容器状态："
docker-compose ps
echo ""

echo "2. 检查应用端口监听："
docker exec rag-translation netstat -tuln | grep 8000 || echo "端口8000未监听！"
echo ""

echo "3. 检查健康检查端点："
docker exec rag-translation curl -s http://localhost:8000/actuator/health || echo "健康检查端点失败！"
echo ""

echo "4. 最近的应用错误日志："
docker-compose logs --tail=50 rag-translation | grep -i "error\|exception\|failed"
echo ""

echo "5. 最近的完整应用日志（最后30行）："
docker-compose logs --tail=30 rag-translation
