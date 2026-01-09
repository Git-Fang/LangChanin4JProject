# RAG Translation System - Docker Desktop 部署镜像
# 第一阶段：构建阶段
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# 复制配置文件
COPY pom.xml .

# 下载依赖（使用多线程加速）
RUN mvn dependency:go-offline -T 1C -DskipTests || true

# 复制源代码
COPY src ./src

# 复制Desktop专用配置
COPY application-desktop.yml ./src/main/resources/application.yml

# 构建应用
RUN mvn clean package -DskipTests -q

# 第二阶段：运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装必要的工具
RUN apk update && \
    apk add --no-cache curl netcat-openbsd bash && \
    rm -rf /var/cache/apk/*

# 创建非root用户
RUN addgroup -g 1000 appuser && \
    adduser -u 1000 -G appuser -s /bin/sh -D appuser

# 复制jar文件
COPY --from=builder /app/target/*.jar app.jar

# 创建必要的目录
RUN mkdir -p /app/logs /app/uploads && \
    chown -R appuser:appuser /app

# 创建启动脚本（等待依赖服务就绪）
RUN echo '#!/bin/bash\n\
set -e\n\
echo "=== RAG Translation System ==="\n\
echo "等待依赖服务就绪..."\n\
\n\
wait_for_service() {\n\
    local host=$1\n\
    local port=$2\n\
    local name=$3\n\
    local timeout=60\n\
    echo "等待 $name ($host:$port)..."\n\
    while [ $timeout -gt 0 ]; do\n\
        if nc -z $host $port 2>/dev/null; then\n\
            echo "$name 已就绪"\n\
            return 0\n\
        fi\n\
        sleep 1\n\
        timeout=$((timeout - 1))\n\
    done\n\
    echo "警告: $name 等待超时"\n\
    return 1\n\
}\n\
\n\
wait_for_service mysql 3306 "MySQL"\n\
wait_for_service mongo 27017 "MongoDB"\n\
wait_for_service redis 6379 "Redis"\n\
wait_for_service qdrant 6333 "Qdrant"\n\
wait_for_service rabbitmq 5672 "RabbitMQ"\n\
\n\
echo "所有依赖服务就绪，启动应用..."\n\
sleep 3\n\
\n\
exec java -Djava.security.egd=file:/dev/./urandom \\\n\
          -XX:+UseG1GC \\\n\
          -XX:+UseStringDeduplication \\\n\
          -Xms512m \\\n\
          -Xmx1024m \\\n\
          -Dserver.address=0.0.0.0 \\\n\
          -Dserver.port=8000 \\\n\
          -jar app.jar' > /app/start.sh && \
    chmod +x /app/start.sh

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 8000

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8000/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["/app/start.sh"]
