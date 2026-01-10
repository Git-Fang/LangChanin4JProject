#!/bin/bash
# Simple Docker deployment for RAGTranslation

echo "🔧 Starting RAGTranslation Docker deployment test..."

# Stop existing containers
docker stop ragtranslation-app 2>/dev/null || true
docker rm ragtranslation-app 2>/dev/null || true

# Build new image
echo "🐳 Building Docker image using existing JAR..."
docker build -t ragtranslation-app:latest .

# Run with simplified configuration
echo "🚀 Starting container with bypass configuration..."
docker run -d --name ragtranslation-app \
  -p 8000:8000 \
  -e SPRING_PROFILES_ACTIVE=docker,docker-bypass \
  -e SPRING_DATASOURCE_URL="jdbc:h2:mem:testdb" \
  -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
  -e SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/chat_db" \
  -e DeepSeek_API_KEY=test \
  -e KIMI_API_KEY=test \
  -e DASHSCOPE_API_KEY=sk-test \
  -e BAIDU_MAP_API_KEY=8qM3bsI6oakw1ICy1g1T9Vo0peSP90of \
  ragtranslation-app:latest

echo "⏳ Waiting for application to start..."
sleep 15

# Health check
if curl -s http://localhost:8000/ | grep -q "小智Agent"; then
    echo "✅ SUCCESS: Docker deployment test completed!"
    echo "🌐 Access URL: http://localhost:8000/unified.html"
    echo "📊 Health check: http://localhost:8000/actuator/health"
    echo "📋 View logs: docker logs ragtranslation-app"
else
    echo "❌ Container failed to properly start"
    echo "📋 Last 30 lines of logs:"
    docker logs ragtranslation-app --tail=30
fi

echo ""
echo "💡 Note: This is a basic deployment test. For production:"
echo "   - Rebuild without bypass profile"
echo "   - Use valid API keys"
echo "   - Connect to proper database services"