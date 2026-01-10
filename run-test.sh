#!/bin/bash
# 测试结果：Docker化运行脚本
# 该脚本启动容器，跳过AI相关配置，仅测试基本的HTTP服务

echo "开始测试Docker化部署..."

# 如果容器存在，则清理
docker stop ragtranslation-test >/dev/null 2>&1
docker rm ragtranslation-test >/dev/null 2>&1

# 启动测试容器，提供基础环境变量
echo "启动测试容器..."
docker run -d --name ragtranslation-test -p 8000:8000 \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e Java_OPTS="-Xmx512m" \
  -e KIMI_API_KEY=test-key \
  -e DeepSeek_API_KEY=test-key \
  -e DASHSCOPE_API_KEY=test-key \
  -e BAIDU_MAP_API_KEY=8qM3bsI6oakw1ICy1g1T9Vo0peSP90of \
  -e SPRING_DATASOURCE_URL="jdbc:h2:mem:testdb" \
  -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
  -e SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/test" \
  -e SPRING_REDIS_HOST=redis \
  -e SPRING_REDIS_PORT=6379 \
  ragtranslation--docker-app:latest

echo "等待容器启动..."
sleep 10

# 检查容器状态
if [ $(docker inspect -f '{{.State.Running}}' ragtranslation-test 2>/dev/null) = "true" ]; then
  echo "容器运行正常，测试HTTP服务..."

  # 尝试访问健康检查端点
  health_status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/actuator/health)

  if [ "$health_status" = "200" ]; then
    echo "✅ 服务启动成功！"
    echo "🌐 访问地址：http://localhost:8000/unified.html"
    echo "📊 健康检查：http://localhost:8000/actuator/health"
    echo "🔍 查看日志：docker logs ragtranslation-test"
  else
    echo "⚠️  容器已启动但服务未就绪（HTTP状态码: $health_status）"
    echo "📋 查看日志："
    docker logs ragtranslation-test --tail=50
  fi
else
  echo "❌ 容器启动失败！"
  echo "📋 错误日志："
  docker logs ragtranslation-test | tail -20
fi

echo ""
echo "使用Docker Deskup可观察容器状态"
echo "如果需要停止：docker stop ragtranslation-test"