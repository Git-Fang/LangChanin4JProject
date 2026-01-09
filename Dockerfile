# 第一阶段：构建阶段
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# 复制配置文件
COPY pom.xml .

# 下载依赖
RUN mvn dependency:go-offline -DskipTests

# 复制源代码
COPY src ./src

# 构建应用
RUN mvn clean package -DskipTests

# 第二阶段：运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装必要的工具
RUN apk update && \
    apk add --no-cache curl netcat-openbsd jq && \
    rm -rf /var/cache/apk/*

# 创建非root用户
RUN addgroup -g 1000 appuser && \
    adduser -u 1000 -G appuser -s /bin/sh -D appuser

# 复制jar文件
COPY --from=builder /app/target/*.jar app.jar

# 创建必要的目录
RUN mkdir -p /app/logs /app/uploads /app/backups && \
    chown -R appuser:appuser /app

# 创建健康检查脚本
RUN echo '#!/bin/sh\nexec curl -f http://localhost:8000/actuator/health || exit 1' > /app/healthcheck.sh && \
    chmod +x /app/healthcheck.sh

# 创建启动脚本
RUN echo '#!/bin/sh\necho "等待依赖服务启动..."\n\n# 等待数据库服务\necho "等待MySQL..."\nfor i in $(seq 1 60); do\n  if nc -z mysql 3306 2>/dev/null; then\n    echo "MySQL已就绪"\n    break\n  fi\n  echo "等待MySQL... ($i/60)"\n  sleep 2\ndone\n\necho "等待MongoDB..."\nfor i in $(seq 1 60); do\n  if nc -z mongo 27017 2>/dev/null; then\n    echo "MongoDB已就绪"\n    break\n  fi\n  echo "等待MongoDB... ($i/60)"\n  sleep 2\ndone\n\necho "等待Redis..."\nfor i in $(seq 1 60); do\n  if nc -z redis 6379 2>/dev/null; then\n    echo "Redis已就绪"\n    break\n  fi\n  echo "等待Redis... ($i/60)"\n  sleep 2\ndone\n\necho "等待Qdrant..."\nfor i in $(seq 1 60); do\n  if nc -z qdrant 6333 2>/dev/null; then\n    echo "Qdrant已就绪"\n    break\n  fi\n  echo "等待Qdrant... ($i/60)"\n  sleep 2\ndone\n\necho "等待RabbitMQ..."\nfor i in $(seq 1 60); do\n  if nc -z rabbitmq 5672 2>/dev/null; then\n    echo "RabbitMQ已就绪"\n    break\n  fi\n  echo "等待RabbitMQ... ($i/60)"\n  sleep 2\ndone\n\necho "所有依赖服务已就绪，启动应用..."\nsleep 5\n\n# 切换到非root用户\nexec su-exec appuser java $JAVA_OPTS \
  -Dserver.port=8000 \
  -Dserver.address=0.0.0.0 \
  -jar app.jar' > /app/start.sh && \
    chmod +x /app/start.sh

# 切换到非root用户
USER appuser

EXPOSE 8000

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD /app/healthcheck.sh

# 启动应用
ENTRYPOINT ["/app/start.sh"]