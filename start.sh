#!/bin/bash
# Docker化一键启动脚本

echo "🚀 一键启动RAGTranslation Docker环境"
echo "==========================================="

# 检查Docker环境
if ! command -v docker >/dev/null 2>&1; then
    echo "❌ Docker未安装或未运行"
    exit 1
fi

# 清理旧容器
echo "🧹 清理旧容器..."
docker rm -f ragtranslation-app 2>/dev/null || true

# 一键运行
echo "🚀 启动容器..."
docker run -d --name ragtranslation-app \
  -p 8000:8000 \
  -e SPRING_PROFILES_ACTIVE=docker,docker-bypass \
  -e SPRING_MAIN_LAZY_INITIALIZATION=true \
  -e SPRING_DATASOURCE_URL="jdbc:h2:mem:testdb" \
  -e DASHSCOPE_API_KEY=test \
  -e KIMI_API_KEY=test \
  -e BAI_MAP_API_KEY=test \
  ragtranslation--docker-app:latest

echo "⏳ 等待服务启动..."
sleep 8

# 测试访问
if curl -s http://localhost:8000/unified.html | grep -q "Agent"; then
    echo "✅ 部署成功！"
    echo "📍 访问地址：http://localhost:8000/unified.html"
else
    echo "⚠️  服务检查中..."
    docker logs ragtranslation-app --tail=10
fi

echo "💡 Docker Desktop: 可查看容器运行状态"
echo "📝 查看日志: docker logs ragtranslation-app"