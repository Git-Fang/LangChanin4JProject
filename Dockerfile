# 第一阶段：构建阶段 - 使用Maven官方镜像（已包含JDK和Maven）
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# 配置Maven使用阿里云仓库（创建settings.xml文件）
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' >> /root/.m2/settings.xml && \
    echo '  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> /root/.m2/settings.xml && \
    echo '  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0' >> /root/.m2/settings.xml && \
    echo '  http://maven.apache.org/xsd/settings-1.0.0.xsd">' >> /root/.m2/settings.xml && \
    echo '  <mirrors>' >> /root/.m2/settings.xml && \
    echo '    <mirror>' >> /root/.m2/settings.xml && \
    echo '      <id>aliyunmaven</id>' >> /root/.m2/settings.xml && \
    echo '      <mirrorOf>*</mirrorOf>' >> /root/.m2/settings.xml && \
    echo '      <name>阿里云公共仓库</name>' >> /root/.m2/settings.xml && \
    echo '      <url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml && \
    echo '    </mirror>' >> /root/.m2/settings.xml && \
    echo '  </mirrors>' >> /root/.m2/settings.xml && \
    echo '</settings>' >> /root/.m2/settings.xml

# 先复制pom.xml，缓存依赖下载
COPY pom.xml .

# 下载所有依赖
RUN mvn dependency:go-offline -DskipTests || true

# 复制源代码
COPY src ./src

# 执行Maven构建，跳过测试
RUN mvn clean package -DskipTests

# 第二阶段：运行阶段 - 使用Alpine Linux JRE镜像（避免网络和DNS问题）
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装必要的包：curl、nodejs、npm和netcat-openbsd
# 使用Alpine的apk包管理器，不需要额外的网络下载脚本
RUN apk update && \
    apk add --no-cache curl ca-certificates netcat-openbsd nodejs npm && \
    npm config set registry https://registry.npmmirror.com && \
    npm install -g @baidumap/mcp-server-baidu-map && \
    rm -rf /var/cache/apk/*

# 复制构建好的jar文件
COPY --from=builder /app/target/RAGTranslation4-1.0-SNAPSHOT.jar app.jar

# 创建启动脚本
RUN echo '#!/bin/sh\n\
echo "等待MongoDB启动..."\n\
for i in $(seq 1 30); do\n\
  if nc -z mongo 27017 >/dev/null 2>&1; then\n\
    echo "MongoDB已就绪"\n\
    sleep 3\n\
    break\n\
  fi\n\
  echo "等待MongoDB... ($i/30)"\n\
  sleep 2\n\
done\n\
echo "启动应用..."\n\
exec java -jar -Dserver.address=0.0.0.0 -Dserver.port=8000 app.jar' > /app/start.sh && \
    chmod +x /app/start.sh

EXPOSE 8000

# 使用启动脚本
ENTRYPOINT ["/app/start.sh"]